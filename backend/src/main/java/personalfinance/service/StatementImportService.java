package personalfinance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personalfinance.entity.Category;
import personalfinance.entity.CategoryRule;
import personalfinance.entity.Transaction;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.CategoryRuleRepository;
import personalfinance.repository.TransactionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatementImportService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleRepository categoryRuleRepository;
    private final QuickParseService quickParseService;
    private final TransactionService transactionService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowRequest {
        private String date; // ISO yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss or dd/MM/yyyy
        private Double amount;
        private String type; // INCOME | EXPENSE
        private String description;
        private Long walletId;
        private Long categoryId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowPreview {
        private int rowIndex;
        private String date;
        private Double amount;
        private String type;
        private String description;
        private Long walletId;
        private Long categoryId;
        private String categoryName;
        private String categoryColor;
        private boolean duplicate;
        private String duplicateReason;
        private boolean valid;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportPreviewResponse {
        private int totalRows;
        private int validRows;
        private int duplicateRows;
        private List<ImportRowPreview> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportCommitResponse {
        private int importedCount;
        private int skippedDuplicates;
        private int skippedInvalid;
        private double totalImportedAmount;
    }

    public ImportPreviewResponse preview(List<ImportRowRequest> rows, Long defaultWalletId, Long userId) {
        List<Transaction> existing = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
        List<Category> categories = categoryRepository.findByUserIsNullOrUserId(userId);
        List<CategoryRule> rules = categoryRuleRepository.findByUserIdOrderByPriorityDescIdDesc(userId);
        return buildPreview(rows, defaultWalletId, existing, categories, rules);
    }

    public ImportPreviewResponse buildPreview(
            List<ImportRowRequest> rows,
            Long defaultWalletId,
            List<Transaction> existing,
            List<Category> categories,
            List<CategoryRule> rules
    ) {
        List<ImportRowPreview> previews = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();
        int validCount = 0;
        int duplicateCount = 0;

        if (rows == null) rows = List.of();

        for (int i = 0; i < rows.size(); i++) {
            ImportRowRequest row = rows.get(i);
            Double rawAmt = row.getAmount();
            if (rawAmt == null || rawAmt == 0) {
                previews.add(ImportRowPreview.builder()
                        .rowIndex(i)
                        .date(row.getDate())
                        .amount(0.0)
                        .type("EXPENSE")
                        .description(row.getDescription())
                        .valid(false)
                        .error("Số tiền không hợp lệ")
                        .build());
                continue;
            }

            String type = row.getType();
            double absAmount = Math.abs(rawAmt);
            if (type == null || type.isBlank()) {
                type = rawAmt > 0 ? "INCOME" : "EXPENSE";
            } else {
                type = type.trim().toUpperCase(Locale.ROOT);
                if (!type.equals("INCOME") && !type.equals("EXPENSE") && !type.equals("TRANSFER")) {
                    type = rawAmt > 0 ? "INCOME" : "EXPENSE";
                }
            }

            LocalDateTime parsedDate = parseFlexibleDate(row.getDate());
            String desc = row.getDescription() != null ? row.getDescription().trim() : "";

            Category matchedCat = null;
            if (row.getCategoryId() != null && row.getCategoryId() > 0 && categories != null) {
                matchedCat = categories.stream()
                        .filter(c -> c.getId().equals(row.getCategoryId()))
                        .findFirst().orElse(null);
            }
            if (matchedCat == null) {
                matchedCat = quickParseService.matchCategoryOnly(desc, type, categories, rules);
            }

            String duplicateReason = findDuplicateInExisting(parsedDate, absAmount, type, desc, existing);

            String batchKey = parsedDate.toLocalDate() + "|" + Math.round(absAmount) + "|" + type + "|" + QuickParseService.normalize(desc);
            if (duplicateReason == null && !seenInBatch.add(batchKey)) {
                duplicateReason = "Trùng lặp với dòng trước trong cùng tệp sao kê";
            }

            boolean isDuplicate = duplicateReason != null;
            if (isDuplicate) duplicateCount++;
            else validCount++;

            previews.add(ImportRowPreview.builder()
                    .rowIndex(i)
                    .date(parsedDate.toString())
                    .amount(absAmount)
                    .type(type)
                    .description(desc.isEmpty() ? "Giao dịch sao kê" : desc)
                    .walletId(row.getWalletId() != null ? row.getWalletId() : defaultWalletId)
                    .categoryId(matchedCat != null ? matchedCat.getId() : null)
                    .categoryName(matchedCat != null ? matchedCat.getName() : null)
                    .categoryColor(matchedCat != null ? matchedCat.getColor() : null)
                    .duplicate(isDuplicate)
                    .duplicateReason(duplicateReason)
                    .valid(true)
                    .build());
        }

        return ImportPreviewResponse.builder()
                .totalRows(previews.size())
                .validRows(validCount)
                .duplicateRows(duplicateCount)
                .rows(previews)
                .build();
    }

    @Transactional
    public ImportCommitResponse commit(List<ImportRowRequest> rows, Long defaultWalletId, boolean skipDuplicates, Long userId) {
        ImportPreviewResponse preview = preview(rows, defaultWalletId, userId);
        int imported = 0;
        int skippedDup = 0;
        int skippedInv = 0;
        double totalAmt = 0.0;

        for (ImportRowPreview row : preview.getRows()) {
            if (!row.isValid()) {
                skippedInv++;
                continue;
            }
            if (skipDuplicates && row.isDuplicate()) {
                skippedDup++;
                continue;
            }
            Long walletId = row.getWalletId() != null ? row.getWalletId() : defaultWalletId;
            if (walletId == null) {
                skippedInv++;
                continue;
            }
            LocalDateTime txDate = parseFlexibleDate(row.getDate());
            transactionService.createTransaction(
                    walletId,
                    null,
                    row.getCategoryId(),
                    row.getType(),
                    row.getAmount(),
                    row.getDescription(),
                    txDate,
                    userId
            );
            imported++;
            totalAmt += row.getAmount();
        }

        return ImportCommitResponse.builder()
                .importedCount(imported)
                .skippedDuplicates(skippedDup)
                .skippedInvalid(skippedInv)
                .totalImportedAmount(totalAmt)
                .build();
    }

    private String findDuplicateInExisting(LocalDateTime date, double amount, String type, String desc, List<Transaction> existing) {
        if (existing == null || existing.isEmpty()) return null;
        String normDesc = QuickParseService.normalize(desc);
        LocalDate targetDay = date.toLocalDate();

        for (Transaction tx : existing) {
            if (tx.getAmount() == null || tx.getTransactionDate() == null) continue;
            if (Math.abs(tx.getAmount() - amount) > 0.5) continue;
            if (tx.getType() != null && !tx.getType().equalsIgnoreCase(type)) continue;

            long dayDiff = Math.abs(ChronoUnit.DAYS.between(tx.getTransactionDate().toLocalDate(), targetDay));
            if (dayDiff > 1) continue;

            String existingDesc = QuickParseService.normalize(tx.getDescription());
            if (dayDiff == 0 && (normDesc.isEmpty() || existingDesc.isEmpty() || existingDesc.equals(normDesc) || existingDesc.contains(normDesc) || normDesc.contains(existingDesc))) {
                return "Đã tồn tại giao dịch cùng ngày & số tiền (ID #" + tx.getId() + ")";
            }
            if (dayDiff == 1 && !normDesc.isEmpty() && existingDesc.equals(normDesc)) {
                return "Trùng mô tả & số tiền với giao dịch gần ngày (ID #" + tx.getId() + ")";
            }
        }
        return null;
    }

    public static LocalDateTime parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        String s = raw.trim().replace("\"", "").replace(",", " ").replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {}

        List<DateTimeFormatter> dtFormats = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss"),
                DateTimeFormatter.ofPattern("d/M/yyyy H:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss d/M/yyyy"),
                DateTimeFormatter.ofPattern("H:mm d/M/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy H:mm:ss"),
                DateTimeFormatter.ofPattern("H:mm:ss d-M-yyyy")
        );
        for (DateTimeFormatter f : dtFormats) {
            try { return LocalDateTime.parse(s, f); } catch (Exception ignored) {}
        }

        List<DateTimeFormatter> dFormats = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/M/d")
        );
        for (DateTimeFormatter f : dFormats) {
            try { return LocalDate.parse(s, f).atTime(12, 0); } catch (Exception ignored) {}
        }
        return LocalDateTime.now();
    }
}
