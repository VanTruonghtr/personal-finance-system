package personalfinance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import personalfinance.entity.Category;
import personalfinance.entity.CategoryRule;
import personalfinance.entity.Wallet;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.CategoryRuleRepository;
import personalfinance.repository.WalletRepository;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QuickParseService {

    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleRepository categoryRuleRepository;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)([+-]?\\d+(?:[.,]\\d+)*)\\s*(tỷ|ty|triệu|trieu|tr|m|ngàn|ngan|nghìn|nghin|k|đ|d|vnd|vnđ)?(?=\\s|$)"
    );

    private static final List<String> INCOME_HINTS = List.of(
            "luong", "thuong", "thu nhap", "nhan tien", "hoan tien", "lai suat",
            "co tuc", "freelance", "ban hang", "duoc cho", "li xi", "hoc bong", "tro cap"
    );

    private static final Map<String, List<String>> BUILTIN_CATEGORY_HINTS = new LinkedHashMap<>();
    static {
        BUILTIN_CATEGORY_HINTS.put("an uong", List.of("an", "com", "pho", "bun", "tra sua", "cafe", "ca phe", "coffee", "an sang", "an trua", "an toi", "nhau", "pizza", "banh mi", "highlands", "starbucks", "kfc", "lotteria", "grabfood", "shopeefood"));
        BUILTIN_CATEGORY_HINTS.put("di chuyen", List.of("grab", "be", "xanh sm", "taxi", "xang", "do xang", "gui xe", "ve xe", "bus", "xe buyt", "ve may bay", "tau", "sua xe", "rua xe"));
        BUILTIN_CATEGORY_HINTS.put("nha o", List.of("tien nha", "tien tro", "thue nha", "dien", "nuoc", "internet", "wifi", "dien thoai", "cuoc", "hoa don", "gas", "chung cu"));
        BUILTIN_CATEGORY_HINTS.put("mua sam", List.of("shopee", "lazada", "tiki", "tiktok shop", "quan ao", "giay", "sieu thi", "bach hoa xanh", "winmart", "coopmart", "aeon", "mua"));
        BUILTIN_CATEGORY_HINTS.put("giai tri", List.of("phim", "cgv", "lotte", "netflix", "spotify", "game", "du lich", "karaoke", "bida", "concert"));
        BUILTIN_CATEGORY_HINTS.put("suc khoe", List.of("thuoc", "kham benh", "benh vien", "nha khoa", "gym", "the thao", "hoc phi", "sach", "khoa hoc", "tieng anh"));
        BUILTIN_CATEGORY_HINTS.put("luong", List.of("luong", "thuong", "freelance", "hoc bong", "hoan tien"));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickParseResult {
        private boolean valid;
        private String error;
        private String rawInput;
        private String type; // EXPENSE | INCOME
        private Double amount;
        private String description;
        private Long walletId;
        private String walletName;
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private String matchedRule;
    }

    public QuickParseResult parseForUser(String rawInput, Long userId) {
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        List<Category> categories = categoryRepository.findByUserIsNullOrUserId(userId);
        List<CategoryRule> rules = categoryRuleRepository.findByUserIdOrderByPriorityDescIdDesc(userId);
        return parse(rawInput, wallets, categories, rules);
    }

    public QuickParseResult parse(String rawInput, List<Wallet> wallets, List<Category> categories, List<CategoryRule> rules) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return QuickParseResult.builder()
                    .valid(false)
                    .rawInput(rawInput)
                    .error("Vui lòng nhập nội dung giao dịch (VD: Ăn trưa 45k ví Momo)")
                    .build();
        }

        String text = rawInput.trim();
        Matcher matcher = AMOUNT_PATTERN.matcher(text);

        Double parsedAmount = null;
        String matchedToken = null;
        int matchStart = -1;
        int matchEnd = -1;
        boolean explicitPlus = false;
        boolean explicitMinus = false;

        while (matcher.find()) {
            String numStr = matcher.group(1);
            String unit = matcher.group(2);
            double candidate = parseVietnameseAmount(numStr, unit);
            if (candidate <= 0) continue;

            boolean hasUnit = unit != null && !unit.isBlank();
            if (parsedAmount == null || hasUnit || candidate >= 1000) {
                parsedAmount = candidate;
                matchedToken = matcher.group(0);
                matchStart = matcher.start();
                matchEnd = matcher.end();
                explicitPlus = numStr.startsWith("+");
                explicitMinus = numStr.startsWith("-");
                if (hasUnit) break;
            }
        }

        if (parsedAmount == null || parsedAmount <= 0) {
            return QuickParseResult.builder()
                    .valid(false)
                    .rawInput(text)
                    .error("Không nhận diện được số tiền (VD: 45k, 1.5tr, 250000)")
                    .build();
        }

        String normalized = normalize(text);

        Wallet matchedWallet = matchWallet(normalized, wallets);

        String type = "EXPENSE";
        if (explicitPlus) {
            type = "INCOME";
        } else if (!explicitMinus) {
            for (String hint : INCOME_HINTS) {
                if (containsWordOrPhrase(normalized, hint)) {
                    type = "INCOME";
                    break;
                }
            }
        }

        CategoryMatch catMatch = matchCategory(normalized, type, categories, rules);
        if (catMatch != null && catMatch.category != null && catMatch.category.getType() != null && !explicitPlus && !explicitMinus) {
            type = catMatch.category.getType();
        }

        String description = buildCleanDescription(text, matchStart, matchEnd, matchedWallet);

        if (matchedWallet == null && wallets != null && !wallets.isEmpty()) {
            matchedWallet = wallets.get(0);
        }

        return QuickParseResult.builder()
                .valid(true)
                .rawInput(text)
                .type(type)
                .amount(parsedAmount)
                .description(description.isEmpty() ? text : description)
                .walletId(matchedWallet != null ? matchedWallet.getId() : null)
                .walletName(matchedWallet != null ? matchedWallet.getName() : null)
                .categoryId(catMatch != null && catMatch.category != null ? catMatch.category.getId() : null)
                .categoryName(catMatch != null && catMatch.category != null ? catMatch.category.getName() : null)
                .categoryColor(catMatch != null && catMatch.category != null ? catMatch.category.getColor() : null)
                .matchedRule(catMatch != null ? catMatch.reason : null)
                .build();
    }

    public Category matchCategoryOnly(String description, String type, List<Category> categories, List<CategoryRule> rules) {
        CategoryMatch match = matchCategory(normalize(description), type, categories, rules);
        return match != null ? match.category : null;
    }

    public double parseVietnameseAmount(String rawNumber, String rawUnit) {
        if (rawNumber == null || rawNumber.isBlank()) return 0;
        String clean = rawNumber.replace("+", "").replace("-", "").trim();
        String unit = rawUnit == null ? "" : normalize(rawUnit);

        double multiplier = 1.0;
        if (unit.equals("k") || unit.equals("ngan") || unit.equals("nghin")) {
            multiplier = 1_000.0;
        } else if (unit.equals("tr") || unit.equals("trieu") || unit.equals("m")) {
            multiplier = 1_000_000.0;
        } else if (unit.equals("ty")) {
            multiplier = 1_000_000_000.0;
        }

        double value;
        if (multiplier > 1.0) {
            String decimalStr = clean.replace(",", ".");
            int lastDot = decimalStr.lastIndexOf('.');
            if (lastDot >= 0 && decimalStr.indexOf('.') != lastDot) {
                decimalStr = decimalStr.replace(".", "");
            }
            value = Double.parseDouble(decimalStr) * multiplier;
        } else {
            String digits = clean.replace(".", "").replace(",", "");
            value = Double.parseDouble(digits);
        }
        return Math.round(value);
    }

    private Wallet matchWallet(String normalizedText, List<Wallet> wallets) {
        if (wallets == null || wallets.isEmpty()) return null;
        Wallet best = null;
        int bestLen = 0;

        for (Wallet w : wallets) {
            if (w.getName() == null || w.getName().isBlank()) continue;
            String normName = normalize(w.getName());
            if (normName.length() > bestLen && containsWordOrPhrase(normalizedText, normName)) {
                best = w;
                bestLen = normName.length();
            }
        }
        if (best != null) return best;

        Map<String, List<String>> walletAliases = Map.of(
                "CASH", List.of("tien mat", "cash", "vi mat"),
                "BANK", List.of("ngan hang", "bank", "the", "vcb", "vietcombank", "techcombank", "tcb", "mb", "mbbank", "acb", "bidv", "tpbank", "vpbank"),
                "EWALLET", List.of("momo", "zalopay", "vnpay", "shopeepay", "vi dien tu")
        );
        for (Wallet w : wallets) {
            List<String> aliases = walletAliases.getOrDefault(w.getType(), List.of());
            for (String alias : aliases) {
                if (containsWordOrPhrase(normalizedText, alias)) {
                    return w;
                }
            }
        }
        return null;
    }

    private record CategoryMatch(Category category, String reason) {}

    private CategoryMatch matchCategory(String normalizedText, String preferredType, List<Category> categories, List<CategoryRule> rules) {
        if (normalizedText == null || normalizedText.isBlank()) return null;

        if (rules != null) {
            for (CategoryRule rule : rules) {
                if (rule.getKeyword() == null || rule.getCategory() == null) continue;
                String normKw = normalize(rule.getKeyword());
                if (!normKw.isEmpty() && containsWordOrPhrase(normalizedText, normKw)) {
                    return new CategoryMatch(rule.getCategory(), "Quy tắc: \"" + rule.getKeyword() + "\"");
                }
            }
        }

        if (categories == null || categories.isEmpty()) return null;

        for (Category c : categories) {
            if (c.getName() == null) continue;
            String normCat = normalize(c.getName());
            if (!normCat.isEmpty() && containsWordOrPhrase(normalizedText, normCat)) {
                return new CategoryMatch(c, "Khớp danh mục: " + c.getName());
            }
        }

        for (Map.Entry<String, List<String>> entry : BUILTIN_CATEGORY_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (containsWordOrPhrase(normalizedText, hint)) {
                    Category found = findCategoryByNormalizedHint(categories, entry.getKey(), preferredType);
                    if (found != null) {
                        return new CategoryMatch(found, "Gợi ý tự động: \"" + hint + "\"");
                    }
                }
            }
        }

        for (Category c : categories) {
            if (preferredType != null && preferredType.equalsIgnoreCase(c.getType())) {
                return new CategoryMatch(c, "Mặc định theo loại");
            }
        }
        return null;
    }

    private Category findCategoryByNormalizedHint(List<Category> categories, String groupKey, String preferredType) {
        for (Category c : categories) {
            if (c.getName() == null) continue;
            String normName = normalize(c.getName());
            if (normName.contains(groupKey)) {
                return c;
            }
        }
        String firstToken = groupKey.split(" ")[0];
        for (Category c : categories) {
            if (c.getName() == null) continue;
            if (preferredType != null && c.getType() != null && !preferredType.equalsIgnoreCase(c.getType())) continue;
            if (normalize(c.getName()).contains(firstToken)) {
                return c;
            }
        }
        return null;
    }

    private String buildCleanDescription(String rawText, int amountStart, int amountEnd, Wallet matchedWallet) {
        StringBuilder sb = new StringBuilder();
        if (amountStart > 0) sb.append(rawText, 0, amountStart);
        sb.append(" ");
        if (amountEnd >= 0 && amountEnd < rawText.length()) sb.append(rawText.substring(amountEnd));

        String cleaned = sb.toString().replaceAll("\\s+", " ").trim();
        if (matchedWallet != null && matchedWallet.getName() != null) {
            String escaped = Pattern.quote(matchedWallet.getName());
            cleaned = cleaned.replaceAll("(?i)(?:bằng|bang|từ|tu|qua|ví|vi)?\\s*" + escaped, " ").trim();
        }
        cleaned = cleaned.replaceAll("(?i)\\b(?:ví|vi|bằng|bang|qua|tài khoản|tk)\\s*$", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String temp = Normalizer.normalize(input, Normalizer.Form.NFD);
        temp = temp.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        temp = temp.replace('đ', 'd').replace('Đ', 'D');
        return temp.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean containsWordOrPhrase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) return false;
        String paddedHaystack = " " + haystack + " ";
        String paddedNeedle = " " + needle + " ";
        return paddedHaystack.contains(paddedNeedle);
    }
}
