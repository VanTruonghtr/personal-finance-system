package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.*;
import personalfinance.repository.*;
import personalfinance.security.CurrentUserService;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class FinancialAdvisorController {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SavingsGoalRepository goalRepository;
    private final CurrentUserService currentUserService;

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
        Long userId = currentUserService.getCurrentUserId();
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

        double safeToSpendPerDay = (daysRemaining > 0 && totalBudget > budgetedSpent)
                ? (totalBudget - budgetedSpent) / daysRemaining
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
            m2.put("detail", totalBudget > 0
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
                String statusIcon = spent > limit ? "🔴 VƯỢT" : (usedPct > timeElapsedPct + 15 ? "🟠 NHANH" : "🟢 ỔN");
                burnRateDetails.add(String.format("- **%s** (%s): đã chi **%s** / %s (**%.0f%%**) — An toàn còn lại: **%s/ngày**",
                        c.getName(), statusIcon, formatVND(spent), formatVND(limit), usedPct, formatVND(safeDaily)));
            }
        }
        double overallSafeDaily = (daysRemaining > 0 && totalBudget > budgetedSpent)
                ? (totalBudget - budgetedSpent) / daysRemaining : 0.0;

        List<String> anomalyList = new ArrayList<>();
        for (Map.Entry<String, Double> entry : monthCatExpense.entrySet()) {
            String cat = entry.getKey();
            double curSpent = entry.getValue();
            double avgHist = prev3MonthsCatSum.getOrDefault(cat, 0.0) / histMonthCount;
            if (avgHist > 0 && curSpent > avgHist * 1.25) {
                double spikePct = ((curSpent - avgHist) / avgHist) * 100.0;
                Transaction bigTx = maxTxByCatInMonth.get(cat);
                String cause = (bigTx != null && bigTx.getDescription() != null && !bigTx.getDescription().isBlank())
                        ? " (khoản lớn nhất: *" + bigTx.getDescription() + "* - " + formatVND(bigTx.getAmount()) + ")"
                        : (bigTx != null ? " (khoản lớn nhất: " + formatVND(bigTx.getAmount()) + ")" : "");
                anomalyList.add(String.format("- **%s**: tăng **+%.0f%%** so với TB %d tháng trước (**%s** vs TB %s)%s",
                        cat, spikePct, histMonthCount, formatVND(curSpent), formatVND(avgHist), cause));
            }
        }

        double dailyRunRate = monthExpense / dayPassed;
        double projectedMonthExpense = dailyRunRate * daysInMonth;
        double projectedEndBalance = monthIncome - projectedMonthExpense;

        StringBuilder reply = new StringBuilder();

        if (containsAny(q, "ngan sach", "han muc", "burn", "moi ngay", "an toan", "tieu bao nhieu", "dinh muc")) {
            reply.append("Chào **").append(userName).append("**, dưới đây là tổng hợp chi tiêu và phân tích **Tốc độ đốt ngân sách (Burn-rate)** cho tháng **").append(targetYmStr).append("**:\n\n");
            reply.append("- Tổng số tiền thực tế bạn đã chi trong tháng: **").append(formatVND(monthExpense)).append("**\n");
            reply.append("- Tiến độ thời gian tháng: **Ngày ").append(dayPassed).append("/").append(daysInMonth).append("** (**").append(Math.round(timeElapsedPct)).append("%** thời gian đã trôi qua, còn **").append(daysRemaining).append(" ngày**).\n");

            if (totalBudget > 0) {
                double totalUsedPct = (budgetedSpent / totalBudget) * 100.0;
                reply.append("- Tổng ngân sách có hạn mức đã dùng: **").append(formatVND(budgetedSpent)).append("** / ").append(formatVND(totalBudget)).append(" (**").append(Math.round(totalUsedPct)).append("%**).\n");
                if (totalBudget >= budgetedSpent && daysRemaining > 0) {
                    reply.append("- 👉 **Hạn mức chi tiêu an toàn còn lại:** **").append(formatVND(overallSafeDaily)).append("/ngày** cho ").append(daysRemaining).append(" ngày cuối tháng.\n\n");
                } else {
                    reply.append("- ⚠️ **Trạng thái:** Bạn đã dùng vượt tổng hạn mức ngân sách đặt ra.\n\n");
                }
                reply.append("**Chi tiết từng danh mục có ngân sách:**\n").append(String.join("\n", burnRateDetails));
            } else {
                reply.append("- Hiện bạn chưa thiết lập hạn mức trần cho danh mục nào cả.");
            }

        } else if (containsAny(q, "du bao", "cuoi thang", "tuong lai", "du dinh", "am tien", "thieu hut")) {
            reply.append("Dựa trên tốc độ chi tiêu thực tế của **").append(userName).append("** trong **").append(dayPassed).append(" ngày** qua của tháng **").append(targetYmStr).append("**:\n\n");
            reply.append("- Tốc độ chi trung bình hiện tại: **").append(formatVND(dailyRunRate)).append("/ngày**.\n");
            reply.append("- Dự phóng tổng chi đến hết ngày ").append(daysInMonth).append(": **").append(formatVND(projectedMonthExpense)).append("**.\n");
            reply.append("- Thu nhập tháng này đã ghi nhận: **").append(formatVND(monthIncome)).append("**.\n\n");
            if (projectedEndBalance >= 0) {
                reply.append("✅ **Kết luận dự báo:** Nếu giữ nguyên nhịp độ hiện tại, cuối tháng bạn sẽ **thặng dư khoảng ").append(formatVND(projectedEndBalance)).append("**.");
            } else {
                double cutPerDay = daysRemaining > 0 ? Math.abs(projectedEndBalance) / daysRemaining : Math.abs(projectedEndBalance);
                reply.append("⚠️ **Cảnh báo dự báo:** Với tốc độ hiện tại, cuối tháng bạn có nguy cơ **âm ").append(formatVND(Math.abs(projectedEndBalance))).append("**. Để cân bằng lại thu chi, bạn cần giảm mức chi xuống khoảng **").append(formatVND(cutPerDay)).append("/ngày** trong ").append(daysRemaining).append(" ngày tới.");
            }

        } else if (containsAny(q, "bat thuong", "tang dot bien", "so sanh", "thang truoc", "bien dong", "tai sao ton")) {
            reply.append("Phân tích **Biến động chi tiêu tháng ").append(targetYmStr).append("** của **").append(userName).append("** so với trung bình **").append(histMonthCount).append(" tháng trước**:\n\n");
            if (!anomalyList.isEmpty()) {
                reply.append("🚨 Phát hiện **").append(anomalyList.size()).append(" danh mục** tăng đột biến (>25%):\n");
                reply.append(String.join("\n", anomalyList));
            } else {
                reply.append("✅ Không phát hiện danh mục nào tăng đột biến bất thường (>25%) so với trung bình các tháng trước. Nhịp độ chi tiêu của bạn đang khá ổn định!");
            }

        } else if (containsAny(q, "muc tieu", "tiet kiem", "tich luy", "mua", "bao lau")) {
            reply.append("Tiến độ **Mục tiêu tiết kiệm** & dự tính thời gian hoàn thành của **").append(userName).append("**:\n\n");
            if (goals.isEmpty()) {
                reply.append("- Hiện bạn chưa tạo mục tiêu tiết kiệm nào. Hãy tạo mục tiêu (ví dụ: Quỹ dự phòng, Mua laptop/xe) để hệ thống tính toán lộ trình tích lũy hàng tháng nhé!");
            } else {
                for (SavingsGoal g : goals) {
                    double cur = g.getCurrentAmount() != null ? g.getCurrentAmount() : 0.0;
                    double target = g.getTargetAmount() != null ? g.getTargetAmount() : 0.0;
                    double remain = Math.max(0, target - cur);
                    int pct = target > 0 ? (int) Math.min(100, Math.round((cur / target) * 100)) : 0;
                    String eta = remain == 0 ? "Đã hoàn thành 🎉"
                            : (avgMonthlySaving > 0 ? String.format("khoảng **%.1f tháng** nữa (với mức tích lũy TB %s/tháng)", remain / avgMonthlySaving, formatVND(avgMonthlySaving))
                            : "cần tạo dòng tiền dương hàng tháng để tính ETA");
                    reply.append("- **").append(g.getName()).append("**: ").append(formatVND(cur)).append(" / ").append(formatVND(target))
                         .append(" (**").append(pct).append("%**) — Còn thiếu **").append(formatVND(remain)).append("** (Dự kiến: ").append(eta).append(").\n");
                }
            }

        } else if (containsAny(q, "vi", "so du", "con bao nhieu tien", "tai khoan")) {
            reply.append("Tổng hợp **Số dư các ví** hiện tại của **").append(userName).append("**:\n\n");
            reply.append("- **Tổng khả dụng:** **").append(formatVND(totalBalance)).append("**\n");
            for (Wallet w : wallets) {
                double bal = w.getBalance() != null ? w.getBalance() : 0.0;
                int share = totalBalance > 0 ? (int) Math.round((bal / totalBalance) * 100) : 0;
                reply.append("- **").append(w.getName()).append("** (").append(w.getStatus()).append("): **").append(formatVND(bal)).append("** (").append(share).append("%)\n");
            }

        } else if (containsAny(q, "nhieu nhat", "top", "chi gi", "phan bo", "50/30/20", "quy tac", "cat giam")) {
            reply.append("Cơ cấu **Top danh mục chi tiêu** kỳ **").append(monthParam).append("** của **").append(userName).append("**:\n\n");
            if (sortedPeriodCats.isEmpty()) {
                reply.append("- Chưa có giao dịch chi tiêu nào trong kỳ này.");
            } else {
                int limit = Math.min(5, sortedPeriodCats.size());
                for (int i = 0; i < limit; i++) {
                    Map.Entry<String, Double> e = sortedPeriodCats.get(i);
                    int pct = periodExpense > 0 ? (int) Math.round((e.getValue() / periodExpense) * 100) : 0;
                    reply.append(i + 1).append(". **").append(e.getKey()).append("**: **").append(formatVND(e.getValue())).append("** (chiếm **").append(pct).append("%** tổng chi)\n");
                }
                Map.Entry<String, Double> top1 = sortedPeriodCats.get(0);
                reply.append("\n💡 **Gợi ý tối ưu:** Nếu bạn tiết chế **15%** ở nhóm **").append(top1.getKey()).append("**, bạn sẽ giữ lại thêm được **").append(formatVND(top1.getValue() * 0.15)).append("**.");
            }

        } else {
            double net = periodIncome - periodExpense;
            double savRate = periodIncome > 0 ? ((periodIncome - periodExpense) / periodIncome) * 100.0 : 0.0;
            reply.append("Chào **").append(userName).append("**, đây là **Báo cáo Phân tích Tài chính Thông minh** (Kỳ: **").append(monthParam).append("**):\n\n");
            reply.append("**1. Dòng tiền & Số dư:**\n");
            reply.append("- Tổng số dư các ví: **").append(formatVND(totalBalance)).append("**\n");
            reply.append("- Tổng thu: **").append(formatVND(periodIncome)).append("** | Tổng chi: **").append(formatVND(periodExpense)).append("** | Chênh lệch ròng: **").append(formatVND(net)).append("** (Tỷ lệ tiết kiệm: **").append(String.format("%.1f%%", savRate)).append("**)\n\n");

            reply.append("**2. Nhịp độ ngân sách & Dự báo tháng ").append(targetYmStr).append(":**\n");
            if (totalBudget > 0) {
                reply.append("- Hạn mức chi an toàn còn lại: **").append(formatVND(overallSafeDaily)).append("/ngày** (còn ").append(daysRemaining).append(" ngày).\n");
            }
            reply.append("- Dự phóng cuối tháng: ").append(projectedEndBalance >= 0
                    ? "Dư khoảng **" + formatVND(projectedEndBalance) + "**"
                    : "Có nguy cơ âm **" + formatVND(Math.abs(projectedEndBalance)) + "**").append(" (tốc độ chi TB ").append(formatVND(dailyRunRate)).append("/ngày).\n\n");

            if (!anomalyList.isEmpty()) {
                reply.append("**3. Cảnh báo biến động bất thường:**\n").append(anomalyList.get(0)).append("\n\n");
            } else if (!sortedPeriodCats.isEmpty()) {
                Map.Entry<String, Double> top1 = sortedPeriodCats.get(0);
                reply.append("**3. Trọng tâm chi tiêu:** Nhóm **").append(top1.getKey()).append("** chiếm tỷ trọng cao nhất (**").append(formatVND(top1.getValue())).append("**).\n\n");
            }

            reply.append("👉 *Bạn có thể hỏi sâu hơn về: **\"Hạn mức chi an toàn mỗi ngày\"**, **\"Dự báo cuối tháng\"**, **\"Chi tiêu bất thường\"**, hoặc **\"Tiến độ mục tiêu tiết kiệm\"**.*");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", reply.toString());
        return ResponseEntity.ok(out);
    }

    private boolean containsAny(String normalizedText, String... keywords) {
        for (String kw : keywords) {
            if (normalizedText.contains(kw)) return true;
        }
        return false;
    }
}