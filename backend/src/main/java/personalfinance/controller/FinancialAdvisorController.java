package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.*;
import personalfinance.repository.*;
import personalfinance.security.CurrentUserService;
import personalfinance.service.GroqAiService;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class FinancialAdvisorController {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SavingsGoalRepository goalRepository;
    private final RecurringTransactionRepository recurringRepository;
    private final CurrentUserService currentUserService;
    private final GroqAiService groqAiService;

    private String formatVND(double amount) {
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(Math.round(amount)) + " ₫";
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String temp = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').trim();
    }

    private YearMonth parseTargetYearMonth(String monthParam) {
        if (monthParam != null && monthParam.matches("\\d{4}-\\d{2}")) {
            try {
                return YearMonth.parse(monthParam);
            } catch (Exception ignored) {
            }
        }
        return YearMonth.now();
    }

    @GetMapping("/analyze")
    public ResponseEntity<?> analyzeFinance(@RequestParam(required = false, defaultValue = "ALL") String month) {
        User currentUser = currentUserService.getCurrentUser();
        Long userId = currentUser.getId();
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        List<Transaction> allTx = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
        List<Category> categories = categoryRepository.findByUserIsNullOrUserId(userId);
        List<SavingsGoal> goals = goalRepository.findByUserId(userId);

        YearMonth targetYm = parseTargetYearMonth(month);
        String targetYmStr = targetYm.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate today = LocalDate.now();
        int daysInMonth = targetYm.lengthOfMonth();
        int dayPassed = targetYm.equals(YearMonth.now()) ? Math.max(1, today.getDayOfMonth()) : daysInMonth;
        int daysRemaining = Math.max(0, daysInMonth - dayPassed);
        double timeElapsedPct = Math.round((dayPassed * 1000.0) / daysInMonth) / 10.0;

        List<Transaction> txs = new ArrayList<>();
        for (Transaction t : allTx) {
            if ("ALL".equalsIgnoreCase(month) || (t.getTransactionDate() != null && t.getTransactionDate().toString().startsWith(month))) {
                txs.add(t);
            }
        }

        double totalBalance = wallets.stream()
                .filter(w -> "ACTIVE".equalsIgnoreCase(w.getStatus()))
                .mapToDouble(w -> w.getBalance() != null ? w.getBalance() : 0.0)
                .sum();

        double totalSaved = goals.stream()
                .mapToDouble(g -> g.getCurrentAmount() != null ? g.getCurrentAmount() : 0.0)
                .sum();

        double totalIncome = 0, totalExpense = 0;
        Map<String, Double> expenseByCat = new HashMap<>();
        Map<Long, Double> monthExpenseByCatId = new HashMap<>();
        double monthTotalExpense = 0;

        for (Transaction t : txs) {
            double amt = t.getAmount() != null ? t.getAmount() : 0.0;
            if ("INCOME".equalsIgnoreCase(t.getType())) {
                totalIncome += amt;
            } else if ("EXPENSE".equalsIgnoreCase(t.getType())) {
                totalExpense += amt;
                String cName = t.getCategory() != null ? t.getCategory().getName() : "Khác";
                expenseByCat.put(cName, expenseByCat.getOrDefault(cName, 0.0) + amt);
            }
        }

        for (Transaction t : allTx) {
            if ("EXPENSE".equalsIgnoreCase(t.getType()) && t.getTransactionDate() != null
                    && t.getTransactionDate().toString().startsWith(targetYmStr)) {
                double amt = t.getAmount() != null ? t.getAmount() : 0.0;
                monthTotalExpense += amt;
                if (t.getCategory() != null) {
                    Long cid = t.getCategory().getId();
                    monthExpenseByCatId.put(cid, monthExpenseByCatId.getOrDefault(cid, 0.0) + amt);
                }
            }
        }

        double netCashflow = totalIncome - totalExpense;
        double savingsRate = totalIncome > 0 ? Math.round(((totalIncome - totalExpense) / totalIncome) * 1000.0) / 10.0 : 0.0;

        String topCategoryName = "Chưa có dữ liệu";
        double topCategoryAmount = 0.0;
        for (Map.Entry<String, Double> entry : expenseByCat.entrySet()) {
            if (entry.getValue() > topCategoryAmount) {
                topCategoryAmount = entry.getValue();
                topCategoryName = entry.getKey();
            }
        }
        int topCategoryPct = totalExpense > 0 ? (int) Math.round((topCategoryAmount / totalExpense) * 100) : 0;

        double totalBudget = 0.0;
        double budgetedSpent = 0.0;
        List<String> overBudgetAlerts = new ArrayList<>();
        List<String> burnRateAlerts = new ArrayList<>();

        for (Category c : categories) {
            if ("EXPENSE".equalsIgnoreCase(c.getType()) && c.getBudgetLimit() != null && c.getBudgetLimit() > 0) {
                double limit = c.getBudgetLimit();
                double spent = monthExpenseByCatId.getOrDefault(c.getId(), 0.0);
                totalBudget += limit;
                budgetedSpent += spent;

                double usedPct = (spent / limit) * 100.0;
                if (spent > limit) {
                    int overPct = (int) Math.round(((spent - limit) / limit) * 100);
                    overBudgetAlerts.add(c.getName() + " vượt " + overPct + "% (+" + formatVND(spent - limit) + ")");
                } else if (usedPct > timeElapsedPct + 15.0 && daysRemaining > 0) {
                    burnRateAlerts.add(c.getName() + " đã dùng " + Math.round(usedPct) + "% (trong khi tháng mới qua " + Math.round(timeElapsedPct) + "%)");
                }
            }
        }

        double effectiveBudget = (currentUser.getMonthlyBudgetLimit() != null && currentUser.getMonthlyBudgetLimit() > 0)
                ? currentUser.getMonthlyBudgetLimit() : totalBudget;
        double effectiveSpent = (currentUser.getMonthlyBudgetLimit() != null && currentUser.getMonthlyBudgetLimit() > 0)
                ? monthTotalExpense : budgetedSpent;
        double safeToSpendPerDay = (daysRemaining > 0 && effectiveBudget > effectiveSpent)
                ? (effectiveBudget - effectiveSpent) / daysRemaining
                : 0.0;

        int healthScore = 50;
        if (totalIncome > 0) {
            if (savingsRate >= 20) healthScore += 25;
            else if (savingsRate > 0) healthScore += 12;
            else healthScore -= 15;
        }
        if (totalBalance > 0) healthScore += 10;
        if (totalSaved > 0) healthScore += 10;
        healthScore -= overBudgetAlerts.size() * 8;
        healthScore -= burnRateAlerts.size() * 4;
        healthScore = Math.max(10, Math.min(100, healthScore));

        String healthLabel = healthScore >= 80 ? "Rất tốt" : healthScore >= 60 ? "Ổn định" : healthScore >= 40 ? "Cần chú ý" : "Báo động đỏ";

        List<Map<String, String>> insights = new ArrayList<>();
        Map<String, String> m1 = new HashMap<>();
        if (totalIncome == 0 && totalExpense > 0) {
            m1.put("level", "WARNING");
            m1.put("title", "Chưa ghi nhận nguồn thu trong kỳ");
            m1.put("detail", "Bạn đã chi " + formatVND(totalExpense) + " nhưng chưa có khoản thu nhập nào trong kỳ này.");
        } else if (savingsRate >= 20) {
            m1.put("level", "GOOD");
            m1.put("title", "Tỷ lệ tiết kiệm đạt chuẩn vàng (" + savingsRate + "%)");
            m1.put("detail", "Dòng tiền thặng dư đạt " + formatVND(netCashflow) + ", vượt mức khuyến nghị 20%.");
        } else if (savingsRate >= 0) {
            m1.put("level", "INFO");
            m1.put("title", "Tỷ lệ tích lũy hiện đạt " + savingsRate + "%");
            m1.put("detail", "Đang giữ lại " + formatVND(netCashflow) + ". Cần tiết kiệm thêm " + formatVND(Math.max(0, totalIncome * 0.2 - netCashflow)) + " để đạt chuẩn.");
        } else {
            m1.put("level", "DANGER");
            m1.put("title", "Cảnh báo bội chi");
            m1.put("detail", "Tổng chi (" + formatVND(totalExpense) + ") cao hơn tổng thu (" + formatVND(totalIncome) + ").");
        }
        insights.add(m1);

        Map<String, String> m2 = new HashMap<>();
        if (!overBudgetAlerts.isEmpty()) {
            m2.put("level", "DANGER");
            m2.put("title", "Có " + overBudgetAlerts.size() + " danh mục vượt trần ngân sách");
            m2.put("detail", "Chi tiết: " + String.join("; ", overBudgetAlerts));
        } else if (!burnRateAlerts.isEmpty()) {
            m2.put("level", "WARNING");
            m2.put("title", "Cảnh báo tốc độ đốt ngân sách (Burn-rate)");
            m2.put("detail", String.join("; ", burnRateAlerts) + ". Hạn mức an toàn còn lại: " + formatVND(safeToSpendPerDay) + "/ngày.");
        } else {
            m2.put("level", "GOOD");
            m2.put("title", "Kiểm soát nhịp độ ngân sách tốt");
            m2.put("detail", effectiveBudget > 0
                    ? "Hạn mức chi tiêu an toàn còn lại: " + formatVND(safeToSpendPerDay) + "/ngày (" + daysRemaining + " ngày cuối tháng)."
                    : "Không có danh mục nào vượt quá hạn mức ngân sách.");
        }
        insights.add(m2);

        if (topCategoryAmount > 0) {
            Map<String, String> m3 = new HashMap<>();
            m3.put("level", topCategoryPct >= 45 ? "WARNING" : "INFO");
            m3.put("title", "Danh mục chi nhiều nhất: " + topCategoryName + " (" + topCategoryPct + "%)");
            m3.put("detail", "Đã chi " + formatVND(topCategoryAmount) + ". Cắt giảm 15% sẽ tiết kiệm thêm " + formatVND(topCategoryAmount * 0.15) + ".");
            insights.add(m3);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("healthScore", healthScore);
        result.put("healthLabel", healthLabel);
        result.put("totalBalance", totalBalance);
        result.put("totalSaved", totalSaved);
        result.put("totalIncome", totalIncome);
        result.put("totalExpense", totalExpense);
        result.put("netCashflow", netCashflow);
        result.put("savingsRate", savingsRate);
        result.put("topCategoryName", topCategoryName);
        result.put("topCategoryAmount", topCategoryAmount);
        result.put("topCategoryPct", topCategoryPct);
        result.put("overBudgetCount", overBudgetAlerts.size());
        result.put("safeToSpendPerDay", Math.round(safeToSpendPerDay));
        result.put("monthTotalExpense", monthTotalExpense);
        result.put("groqConfigured", groqAiService.isConfigured());
        result.put("insights", insights);
        return ResponseEntity.ok(result);
    }

    @Data
    public static class AskRequest {
        private String question;
        private String month;
        private String userName;
        private List<Map<String, Object>> history;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> askAdvisor(@RequestBody AskRequest req) {
        User currentUser = currentUserService.getCurrentUser();
        Long userId = currentUser.getId();

        String rawQuestion = req.getQuestion() != null ? req.getQuestion().trim() : "";
        String q = normalizeText(rawQuestion);
        String monthParam = req.getMonth() != null ? req.getMonth() : "ALL";
        String userName = (req.getUserName() != null && !req.getUserName().isBlank())
                ? req.getUserName().trim()
                : (currentUser.getFullName() != null && !currentUser.getFullName().isBlank() ? currentUser.getFullName().trim() : "bạn");

        List<Wallet> wallets = walletRepository.findByUserId(userId);
        List<Transaction> allTx = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
        List<Category> categories = categoryRepository.findByUserIsNullOrUserId(userId);
        List<SavingsGoal> goals = goalRepository.findByUserId(userId);
        List<RecurringTransaction> recurrings = recurringRepository.findByUserId(userId);

        YearMonth targetYm = parseTargetYearMonth(monthParam);
        String targetYmStr = targetYm.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate today = LocalDate.now();
        int daysInMonth = targetYm.lengthOfMonth();
        int dayPassed = targetYm.equals(YearMonth.now()) ? Math.max(1, today.getDayOfMonth()) : daysInMonth;
        int daysRemaining = Math.max(0, daysInMonth - dayPassed);
        double timeElapsedPct = (dayPassed * 100.0) / daysInMonth;

        double totalBalance = wallets.stream()
                .filter(w -> "ACTIVE".equalsIgnoreCase(w.getStatus()))
                .mapToDouble(w -> w.getBalance() != null ? w.getBalance() : 0.0).sum();

        double periodIncome = 0, periodExpense = 0;
        double monthIncome = 0, monthExpense = 0;
        Map<String, Double> periodCatExpense = new LinkedHashMap<>();
        Map<String, Double> monthCatExpense = new LinkedHashMap<>();
        Map<Long, Double> monthCatIdExpense = new HashMap<>();
        Map<String, Transaction> maxTxByCatInMonth = new HashMap<>();

        Set<String> prev3Months = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            prev3Months.add(targetYm.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        Map<String, Double> prev3MonthsCatSum = new HashMap<>();
        Set<String> activePrevMonths = new HashSet<>();
        double prev3MonthsTotalExpense = 0;
        double prev3MonthsTotalIncome = 0;

        for (Transaction t : allTx) {
            double amt = t.getAmount() != null ? t.getAmount() : 0.0;
            String dStr = t.getTransactionDate() != null ? t.getTransactionDate().toString() : "";
            String ymStr = dStr.length() >= 7 ? dStr.substring(0, 7) : "";
            String cName = t.getCategory() != null ? t.getCategory().getName() : "Khác";

            if ("ALL".equalsIgnoreCase(monthParam) || dStr.startsWith(monthParam)) {
                if ("INCOME".equalsIgnoreCase(t.getType())) periodIncome += amt;
                else if ("EXPENSE".equalsIgnoreCase(t.getType())) {
                    periodExpense += amt;
                    periodCatExpense.put(cName, periodCatExpense.getOrDefault(cName, 0.0) + amt);
                }
            }

            if (dStr.startsWith(targetYmStr)) {
                if ("INCOME".equalsIgnoreCase(t.getType())) {
                    monthIncome += amt;
                } else if ("EXPENSE".equalsIgnoreCase(t.getType())) {
                    monthExpense += amt;
                    monthCatExpense.put(cName, monthCatExpense.getOrDefault(cName, 0.0) + amt);
                    if (t.getCategory() != null) {
                        Long cid = t.getCategory().getId();
                        monthCatIdExpense.put(cid, monthCatIdExpense.getOrDefault(cid, 0.0) + amt);
                    }
                    Transaction curMax = maxTxByCatInMonth.get(cName);
                    if (curMax == null || amt > (curMax.getAmount() != null ? curMax.getAmount() : 0.0)) {
                        maxTxByCatInMonth.put(cName, t);
                    }
                }
            }

            if (prev3Months.contains(ymStr)) {
                activePrevMonths.add(ymStr);
                if ("EXPENSE".equalsIgnoreCase(t.getType())) {
                    prev3MonthsTotalExpense += amt;
                    prev3MonthsCatSum.put(cName, prev3MonthsCatSum.getOrDefault(cName, 0.0) + amt);
                } else if ("INCOME".equalsIgnoreCase(t.getType())) {
                    prev3MonthsTotalIncome += amt;
                }
            }
        }

        int histMonthCount = Math.max(1, activePrevMonths.size());
        double avgMonthlySaving = activePrevMonths.isEmpty()
                ? Math.max(0, monthIncome - monthExpense)
                : Math.max(0, (prev3MonthsTotalIncome - prev3MonthsTotalExpense) / histMonthCount);

        List<Map.Entry<String, Double>> sortedPeriodCats = new ArrayList<>(periodCatExpense.entrySet());
        sortedPeriodCats.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double totalBudget = 0, budgetedSpent = 0;
        List<String> burnRateDetails = new ArrayList<>();
        for (Category c : categories) {
            if ("EXPENSE".equalsIgnoreCase(c.getType()) && c.getBudgetLimit() != null && c.getBudgetLimit() > 0) {
                double limit = c.getBudgetLimit();
                double spent = monthCatIdExpense.getOrDefault(c.getId(), 0.0);
                totalBudget += limit;
                budgetedSpent += spent;
                double usedPct = (spent / limit) * 100.0;
                double safeDaily = daysRemaining > 0 ? Math.max(0, (limit - spent) / daysRemaining) : 0;
                String statusIcon = spent > limit ? "VƯỢT" : (usedPct > timeElapsedPct + 15 ? "NHANH" : "ỔN");
                burnRateDetails.add(String.format("- %s (%s): đã chi %s / %s (%.0f%%), còn an toàn %s/ngày",
                        c.getName(), statusIcon, formatVND(spent), formatVND(limit), usedPct, formatVND(safeDaily)));
            }
        }
        double effectiveBudget = (currentUser.getMonthlyBudgetLimit() != null && currentUser.getMonthlyBudgetLimit() > 0)
                ? currentUser.getMonthlyBudgetLimit() : totalBudget;
        double effectiveSpent = (currentUser.getMonthlyBudgetLimit() != null && currentUser.getMonthlyBudgetLimit() > 0)
                ? monthExpense : budgetedSpent;
        double overallSafeDaily = (daysRemaining > 0 && effectiveBudget > effectiveSpent)
                ? (effectiveBudget - effectiveSpent) / daysRemaining : 0.0;

        List<String> anomalyList = new ArrayList<>();
        for (Map.Entry<String, Double> entry : monthCatExpense.entrySet()) {
            String cat = entry.getKey();
            double curSpent = entry.getValue();
            double avgHist = prev3MonthsCatSum.getOrDefault(cat, 0.0) / histMonthCount;
            if (avgHist > 0 && curSpent > avgHist * 1.25) {
                double spikePct = ((curSpent - avgHist) / avgHist) * 100.0;
                Transaction bigTx = maxTxByCatInMonth.get(cat);
                String cause = (bigTx != null && bigTx.getDescription() != null && !bigTx.getDescription().isBlank())
                        ? " (khoản lớn nhất: " + bigTx.getDescription() + " - " + formatVND(bigTx.getAmount()) + ")"
                        : (bigTx != null ? " (khoản lớn nhất: " + formatVND(bigTx.getAmount()) + ")" : "");
                anomalyList.add(String.format("- %s: tăng +%.0f%% so với TB %d tháng trước (%s vs TB %s)%s",
                        cat, spikePct, histMonthCount, formatVND(curSpent), formatVND(avgHist), cause));
            }
        }

        double dailyRunRate = monthExpense / dayPassed;
        double projectedMonthExpense = dailyRunRate * daysInMonth;
        double projectedEndBalance = monthIncome - projectedMonthExpense;

        String systemPrompt = buildFinancialSystemPrompt(
                userName, today, monthParam, targetYmStr, dayPassed, daysInMonth, daysRemaining, timeElapsedPct,
                totalBalance, wallets, periodIncome, periodExpense, monthIncome, monthExpense,
                effectiveBudget, overallSafeDaily, dailyRunRate, projectedMonthExpense, projectedEndBalance,
                sortedPeriodCats, burnRateDetails, anomalyList, goals, recurrings, allTx, avgMonthlySaving
        );

        GroqAiService.AiReply groqReply = groqAiService.chat(systemPrompt, req.getHistory(), rawQuestion);
        if (groqReply.isFromGroq()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", groqReply.getAnswer());
            out.put("provider", "groq");
            out.put("model", groqReply.getModel());
            return ResponseEntity.ok(out);
        }

        String fallbackAnswer = buildSmartLocalFallback(
                rawQuestion, q, userName, monthParam, targetYmStr, dayPassed, daysInMonth, daysRemaining, timeElapsedPct,
                totalBalance, wallets, periodIncome, periodExpense, monthIncome, monthExpense,
                effectiveBudget, effectiveSpent, overallSafeDaily, dailyRunRate, projectedMonthExpense, projectedEndBalance,
                sortedPeriodCats, burnRateDetails, anomalyList, goals, recurrings, allTx, avgMonthlySaving
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", fallbackAnswer);
        out.put("provider", "fallback");
        out.put("model", "local-engine");
        if (groqReply.getErrorReason() != null) {
            out.put("warning", groqReply.getErrorReason());
        }
        return ResponseEntity.ok(out);
    }

    private String buildFinancialSystemPrompt(
            String userName, LocalDate today, String monthParam, String targetYmStr,
            int dayPassed, int daysInMonth, int daysRemaining, double timeElapsedPct,
            double totalBalance, List<Wallet> wallets,
            double periodIncome, double periodExpense, double monthIncome, double monthExpense,
            double effectiveBudget, double overallSafeDaily, double dailyRunRate,
            double projectedMonthExpense, double projectedEndBalance,
            List<Map.Entry<String, Double>> sortedPeriodCats, List<String> burnRateDetails,
            List<String> anomalyList, List<SavingsGoal> goals, List<RecurringTransaction> recurrings,
            List<Transaction> allTx, double avgMonthlySaving
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là Trợ lý Tài chính Cá nhân thông minh của hệ thống HTR FinMind.\n");
        sb.append("Nguyên tắc trả lời:\n");
        sb.append("- Trả lời hoàn toàn bằng tiếng Việt tự nhiên, thân thiện, sắc bén và đi thẳng vào câu hỏi của người dùng.\n");
        sb.append("- TUYỆT ĐỐI chỉ sử dụng số liệu thật trong dữ liệu dưới đây; không tự bịa con số. Nếu thiếu dữ liệu hãy nói rõ.\n");
        sb.append("- Trình bày gọn gàng bằng Markdown (**in đậm** con số quan trọng, gạch đầu dòng ngắn gọn), không lặp lại nguyên báo cáo dài nếu người dùng chỉ hỏi 1 câu ngắn.\n");
        sb.append("- Nếu người dùng hỏi lời khuyên (mua sắm, cắt giảm, lập kế hoạch, tiết kiệm, đầu tư), hãy tính toán cụ thể dựa trên số dư, thu nhập, chi tiêu, mục tiêu và hạn mức thực tế rồi đưa ra khuyến nghị rõ ràng.\n\n");

        sb.append("=== HỒ SƠ TÀI CHÍNH THỜI GIAN THỰC ===\n");
        sb.append("Người dùng: ").append(userName).append(" | Hôm nay: ").append(today).append("\n");
        sb.append("Kỳ đang lọc trên giao diện: ").append(monthParam).append(" | Tháng phân tích: ").append(targetYmStr)
          .append(String.format(" (ngày %d/%d = %.1f%% tháng, còn %d ngày)\n", dayPassed, daysInMonth, timeElapsedPct, daysRemaining));

        sb.append("\n1. TỔNG QUAN VÍ TIỀN (Tổng khả dụng: ").append(formatVND(totalBalance)).append("):\n");
        if (wallets.isEmpty()) sb.append("- Chưa có ví nào.\n");
        for (Wallet w : wallets) {
            sb.append("- ").append(w.getName()).append(" [").append(w.getType()).append(", ").append(w.getStatus()).append("]: ")
              .append(formatVND(w.getBalance() != null ? w.getBalance() : 0.0)).append("\n");
        }

        double periodNet = periodIncome - periodExpense;
        double periodSaveRate = periodIncome > 0 ? (periodNet * 100.0 / periodIncome) : 0.0;
        sb.append("\n2. DÒNG TIỀN:\n");
        sb.append(String.format("- Kỳ đang lọc (%s): Thu %s | Chi %s | Ròng %s (Tỷ lệ tiết kiệm %.1f%%)\n",
                monthParam, formatVND(periodIncome), formatVND(periodExpense), formatVND(periodNet), periodSaveRate));
        sb.append(String.format("- Riêng tháng %s: Thu %s | Chi %s | Tốc độ chi TB %s/ngày | Dự phóng hết tháng chi %s (dự kiến cuối tháng %s)\n",
                targetYmStr, formatVND(monthIncome), formatVND(monthExpense), formatVND(dailyRunRate),
                formatVND(projectedMonthExpense), formatVND(projectedEndBalance)));
        sb.append("- Tích lũy trung bình tháng: ").append(formatVND(avgMonthlySaving)).append("\n");

        sb.append("\n3. NGÂN SÁCH & BURN-RATE:\n");
        if (effectiveBudget > 0) {
            sb.append("- Ngân sách tháng: ").append(formatVND(effectiveBudget))
              .append(" | Hạn mức an toàn còn lại: ").append(formatVND(overallSafeDaily)).append("/ngày\n");
        } else {
            sb.append("- Chưa đặt ngân sách tổng tháng.\n");
        }
        for (String b : burnRateDetails) sb.append(b).append("\n");

        sb.append("\n4. TOP DANH MỤC CHI TIÊU (Kỳ ").append(monthParam).append("):\n");
        int catLimit = Math.min(8, sortedPeriodCats.size());
        for (int i = 0; i < catLimit; i++) {
            Map.Entry<String, Double> e = sortedPeriodCats.get(i);
            double pct = periodExpense > 0 ? (e.getValue() * 100.0 / periodExpense) : 0.0;
            sb.append(String.format("- %s: %s (%.1f%%)\n", e.getKey(), formatVND(e.getValue()), pct));
        }

        if (!anomalyList.isEmpty()) {
            sb.append("\n5. CẢNH BÁO TĂNG ĐỘT BIẾN SO VỚI TB 3 THÁNG:\n");
            for (String a : anomalyList) sb.append(a).append("\n");
        }

        sb.append("\n6. MỤC TIÊU TIẾT KIỆM:\n");
        if (goals.isEmpty()) sb.append("- Chưa có mục tiêu tiết kiệm.\n");
        for (SavingsGoal g : goals) {
            double cur = g.getCurrentAmount() != null ? g.getCurrentAmount() : 0.0;
            double tgt = g.getTargetAmount() != null ? g.getTargetAmount() : 0.0;
            sb.append(String.format("- %s: %s / %s (còn thiếu %s, hạn %s)\n",
                    g.getName(), formatVND(cur), formatVND(tgt), formatVND(Math.max(0, tgt - cur)),
                    g.getDeadline() != null ? g.getDeadline().toString() : "không đặt"));
        }

        if (recurrings != null && !recurrings.isEmpty()) {
            sb.append("\n7. KHOẢN THU/CHI ĐỊNH KỲ:\n");
            for (RecurringTransaction r : recurrings) {
                sb.append(String.format("- %s (%s, %s): %s, kỳ tiếp theo %s\n",
                        r.getTitle(), r.getType(), r.getFrequency(), formatVND(r.getAmount() != null ? r.getAmount() : 0.0),
                        r.getNextDueDate() != null ? r.getNextDueDate().toString() : "-"));
            }
        }

        sb.append("\n8. 15 GIAO DỊCH GẦN NHẤT:\n");
        int txLimit = Math.min(15, allTx.size());
        for (int i = 0; i < txLimit; i++) {
            Transaction t = allTx.get(i);
            String dt = t.getTransactionDate() != null ? t.getTransactionDate().toLocalDate().toString() : "";
            String cat = t.getCategory() != null ? t.getCategory().getName() : "Khác";
            String wName = t.getWallet() != null ? t.getWallet().getName() : "";
            sb.append(String.format("- %s | %s | %s | %s | %s | %s\n",
                    dt, t.getType(), formatVND(t.getAmount() != null ? t.getAmount() : 0.0), cat, wName,
                    t.getDescription() != null ? t.getDescription() : "-"));
        }

        return sb.toString();
    }

    private String buildSmartLocalFallback(
            String rawQuestion, String q, String userName, String monthParam, String targetYmStr,
            int dayPassed, int daysInMonth, int daysRemaining, double timeElapsedPct,
            double totalBalance, List<Wallet> wallets,
            double periodIncome, double periodExpense, double monthIncome, double monthExpense,
            double effectiveBudget, double effectiveSpent, double overallSafeDaily,
            double dailyRunRate, double projectedMonthExpense, double projectedEndBalance,
            List<Map.Entry<String, Double>> sortedPeriodCats, List<String> burnRateDetails,
            List<String> anomalyList, List<SavingsGoal> goals, List<RecurringTransaction> recurrings,
            List<Transaction> allTx, double avgMonthlySaving
    ) {
        StringBuilder reply = new StringBuilder();

        if (q.matches("^(xin chao|chao|hello|hi|hey|alo|chao ban|chao ai).*") && q.length() <= 30) {
            return "Chào **" + userName + "**! Mình đã nắm toàn bộ số liệu ví (**" + formatVND(totalBalance)
                    + "**), thu chi tháng **" + targetYmStr + "** và mục tiêu tiết kiệm của bạn. Bạn muốn mình kiểm tra giao dịch gần nhất, tính hạn mức an toàn mỗi ngày, hay tư vấn kế hoạch mua sắm/tiết kiệm?";
        }

        Double mentionedAmt = extractMentionedAmount(rawQuestion);
        if (mentionedAmt != null && mentionedAmt >= 10_000 && containsAny(q, "mua", "du tien", "nen mua", "chi duoc", "co du", "kha nang", "tra gop")) {
            reply.append("Phân tích khả năng chi trả cho khoản **").append(formatVND(mentionedAmt)).append("**:\n\n");
            reply.append("- Số dư khả dụng hiện tại: **").append(formatVND(totalBalance)).append("**\n");
            if (totalBalance >= mentionedAmt) {
                double afterBal = totalBalance - mentionedAmt;
                reply.append("- Sau khi chi, bạn còn lại **").append(formatVND(afterBal)).append("**");
                if (monthExpense > 0) {
                    double monthsCover = afterBal / Math.max(1, monthExpense);
                    reply.append(String.format(" (đủ dự phòng khoảng **%.1f tháng** chi tiêu).", monthsCover));
                }
                reply.append("\n- 💡 **Lời khuyên:** ").append(afterBal >= monthExpense * 2
                        ? "Bạn hoàn toàn đủ khả năng chi trả mà vẫn giữ được quỹ dự phòng an toàn."
                        : "Bạn đủ tiền mặt, nhưng sau khi mua quỹ dự phòng khá mỏng — nên cân nhắc chờ thêm hoặc chia ngân sách hợp lý.");
            } else {
                double shortage = mentionedAmt - totalBalance;
                reply.append("- Hiện bạn còn thiếu **").append(formatVND(shortage)).append("**.\n");
                if (avgMonthlySaving > 0) {
                    reply.append(String.format("- Với mức tích lũy trung bình **%s/tháng**, bạn sẽ cần khoảng **%.1f tháng** nữa để đạt đủ.", formatVND(avgMonthlySaving), shortage / avgMonthlySaving));
                } else {
                    reply.append("- Bạn nên thiết lập một **Mục tiêu tiết kiệm** và đặt hạn mức chi tiêu tháng để tích lũy dần cho khoản này.");
                }
            }
            return reply.toString();
        }

        if (containsAny(q, "gan day", "gan nhat", "vua mua", "hom nay", "moi nhat", "lich su", "giao dich")) {
            reply.append("**5 giao dịch gần nhất** của **").append(userName).append("**:\n\n");
            if (allTx.isEmpty()) {
                reply.append("- Chưa có giao dịch nào được ghi nhận.");
            } else {
                int lim = Math.min(5, allTx.size());
                for (int i = 0; i < lim; i++) {
                    Transaction t = allTx.get(i);
                    String dt = t.getTransactionDate() != null ? t.getTransactionDate().toLocalDate().toString() : "";
                    String cat = t.getCategory() != null ? t.getCategory().getName() : "Khác";
                    String sign = "INCOME".equalsIgnoreCase(t.getType()) ? "+" : "EXPENSE".equalsIgnoreCase(t.getType()) ? "-" : "";
                    reply.append(String.format("- **%s**: %s**%s** — %s (%s)\n",
                            dt, sign, formatVND(t.getAmount() != null ? t.getAmount() : 0.0),
                            t.getDescription() != null && !t.getDescription().isBlank() ? t.getDescription() : "Giao dịch", cat));
                }
            }
            return reply.toString();
        }

        if (containsAny(q, "ngan sach", "han muc", "burn", "moi ngay", "an toan", "tieu bao nhieu", "dinh muc")) {
            reply.append("Phân tích **Ngân sách & Burn-rate** tháng **").append(targetYmStr).append("**:\n\n");
            reply.append("- Thực chi tháng này: **").append(formatVND(monthExpense)).append("** (Ngày ").append(dayPassed).append("/").append(daysInMonth).append(" = **").append(Math.round(timeElapsedPct)).append("%** tháng).\n");
            if (effectiveBudget > 0) {
                double usedPct = (effectiveSpent / effectiveBudget) * 100.0;
                reply.append("- Ngân sách đã dùng: **").append(formatVND(effectiveSpent)).append("** / ").append(formatVND(effectiveBudget)).append(" (**").append(Math.round(usedPct)).append("%**).\n");
                reply.append("- 👉 Hạn mức an toàn còn lại: **").append(formatVND(overallSafeDaily)).append("/ngày** (còn ").append(daysRemaining).append(" ngày).\n\n");
                if (!burnRateDetails.isEmpty()) {
                    reply.append("**Chi tiết theo danh mục:**\n").append(String.join("\n", burnRateDetails));
                }
            } else {
                reply.append("- Bạn chưa đặt ngân sách tổng hoặc hạn mức danh mục. Hãy nhấn vào hồ sơ ở góc dưới trái để thiết lập nhé.");
            }
            return reply.toString();
        }

        if (containsAny(q, "du bao", "cuoi thang", "tuong lai", "am tien", "thieu hut")) {
            reply.append("Dự báo dòng tiền cuối tháng **").append(targetYmStr).append("**:\n\n");
            reply.append("- Tốc độ chi trung bình: **").append(formatVND(dailyRunRate)).append("/ngày** → Dự phóng cả tháng: **").append(formatVND(projectedMonthExpense)).append("**.\n");
            reply.append("- Thu nhập tháng này: **").append(formatVND(monthIncome)).append("**.\n");
            reply.append(projectedEndBalance >= 0
                    ? "- ✅ Dự kiến cuối tháng **thặng dư " + formatVND(projectedEndBalance) + "**."
                    : "- ⚠️ Nguy cơ **âm " + formatVND(Math.abs(projectedEndBalance)) + "** nếu giữ nhịp chi hiện tại.");
            return reply.toString();
        }

        if (containsAny(q, "bat thuong", "tang dot bien", "so sanh", "thang truoc", "bien dong", "tai sao ton")) {
            reply.append("Phân tích **Biến động chi tiêu tháng ").append(targetYmStr).append("** so với TB các tháng trước:\n\n");
            if (!anomalyList.isEmpty()) {
                reply.append(String.join("\n", anomalyList));
            } else {
                reply.append("✅ Không có danh mục nào tăng đột biến (>25%) so với trung bình các tháng trước.");
            }
            return reply.toString();
        }

        if (containsAny(q, "muc tieu", "tiet kiem", "tich luy", "bao lau")) {
            reply.append("Tiến độ **Mục tiêu tiết kiệm** của **").append(userName).append("**:\n\n");
            if (goals.isEmpty()) {
                reply.append("- Bạn chưa tạo mục tiêu tiết kiệm nào.");
            } else {
                for (SavingsGoal g : goals) {
                    double cur = g.getCurrentAmount() != null ? g.getCurrentAmount() : 0.0;
                    double target = g.getTargetAmount() != null ? g.getTargetAmount() : 0.0;
                    double remain = Math.max(0, target - cur);
                    int pct = target > 0 ? (int) Math.min(100, Math.round((cur / target) * 100)) : 0;
                    reply.append("- **").append(g.getName()).append("**: ").append(formatVND(cur)).append(" / ").append(formatVND(target))
                         .append(" (**").append(pct).append("%**, còn thiếu **").append(formatVND(remain)).append("**)\n");
                }
            }
            return reply.toString();
        }

        if (containsAny(q, "vi", "so du", "con bao nhieu tien", "tai khoan")) {
            reply.append("Tổng hợp **Số dư các ví** (Tổng khả dụng: **").append(formatVND(totalBalance)).append("**):\n\n");
            for (Wallet w : wallets) {
                double bal = w.getBalance() != null ? w.getBalance() : 0.0;
                reply.append("- **").append(w.getName()).append("**: **").append(formatVND(bal)).append("**\n");
            }
            return reply.toString();
        }

        if (containsAny(q, "nhieu nhat", "top", "chi gi", "phan bo", "50/30/20", "cat giam", "qua tay")) {
            reply.append("Top danh mục chi nhiều nhất kỳ **").append(monthParam).append("**:\n\n");
            if (sortedPeriodCats.isEmpty()) {
                reply.append("- Chưa có giao dịch chi tiêu nào trong kỳ này.");
            } else {
                int lim = Math.min(5, sortedPeriodCats.size());
                for (int i = 0; i < lim; i++) {
                    Map.Entry<String, Double> e = sortedPeriodCats.get(i);
                    int pct = periodExpense > 0 ? (int) Math.round((e.getValue() / periodExpense) * 100) : 0;
                    reply.append(i + 1).append(". **").append(e.getKey()).append("**: **").append(formatVND(e.getValue())).append("** (").append(pct).append("%)\n");
                }
                Map.Entry<String, Double> top1 = sortedPeriodCats.get(0);
                reply.append("\n💡 Cắt giảm **15%** ở nhóm **").append(top1.getKey()).append("** sẽ giúp bạn giữ lại thêm **").append(formatVND(top1.getValue() * 0.15)).append("**.");
            }
            return reply.toString();
        }

        if (containsAny(q, "dinh ky", "hoa don", "sap den han")) {
            reply.append("Danh sách **Khoản thu/chi định kỳ**:\n\n");
            if (recurrings == null || recurrings.isEmpty()) {
                reply.append("- Chưa có khoản định kỳ nào.");
            } else {
                for (RecurringTransaction r : recurrings) {
                    reply.append("- **").append(r.getTitle()).append("**: **").append(formatVND(r.getAmount() != null ? r.getAmount() : 0.0))
                         .append("** (Hạn tiếp theo: ").append(r.getNextDueDate()).append(")\n");
                }
            }
            return reply.toString();
        }

        double net = periodIncome - periodExpense;
        double savRate = periodIncome > 0 ? (net * 100.0 / periodIncome) : 0.0;
        reply.append("Tóm tắt nhanh tài chính kỳ **").append(monthParam).append("** của **").append(userName).append("**:\n\n");
        reply.append("- **Số dư khả dụng:** ").append(formatVND(totalBalance)).append("\n");
        reply.append("- **Thu / Chi:** +").append(formatVND(periodIncome)).append(" / -").append(formatVND(periodExpense))
             .append(String.format(" (Tỷ lệ tiết kiệm **%.1f%%**)\n", savRate));
        if (effectiveBudget > 0) {
            reply.append("- **An toàn mỗi ngày:** ").append(formatVND(overallSafeDaily)).append("/ngày (còn ").append(daysRemaining).append(" ngày).\n");
        }
        if (!sortedPeriodCats.isEmpty()) {
            reply.append("- **Chi nhiều nhất:** ").append(sortedPeriodCats.get(0).getKey()).append(" (").append(formatVND(sortedPeriodCats.get(0).getValue())).append(").\n");
        }
        return reply.toString();
    }

    private Double extractMentionedAmount(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)*)\\s*(tr|triệu|trieu|m|k|ngàn|nghìn|đ|vnd)?").matcher(text);
        while (m.find()) {
            String num = m.group(1);
            String unit = m.group(2) != null ? normalizeText(m.group(2)) : "";
            try {
                if (unit.equals("tr") || unit.equals("trieu") || unit.equals("m")) {
                    return Double.parseDouble(num.replace(",", ".")) * 1_000_000.0;
                }
                if (unit.equals("k") || unit.equals("ngan") || unit.equals("nghin")) {
                    return Double.parseDouble(num.replace(",", ".")) * 1_000.0;
                }
                double raw = Double.parseDouble(num.replace(".", "").replace(",", ""));
                if (raw >= 10_000) return raw;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean containsAny(String normalizedText, String... keywords) {
        for (String kw : keywords) {
            if (normalizedText.contains(kw)) return true;
        }
        return false;
    }
}
