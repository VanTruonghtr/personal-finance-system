package personalfinance.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import personalfinance.entity.Category;
import personalfinance.entity.Transaction;
import personalfinance.entity.User;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.TransactionRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BurnRateItem {
        private Long categoryId;
        private String categoryName;
        private String color;
        private double budgetLimit;
        private double spent;
        private double remaining;
        private double safeToSpendPerDay;
        private double spentPct;
        private double timeElapsedPct;
        private double burnRateRatio;
        private String status; // SAFE | WARNING | DANGER | EXCEEDED
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopTxItem {
        private Long id;
        private String description;
        private double amount;
        private String date;
        private String walletName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAnomaly {
        private Long categoryId;
        private String categoryName;
        private String color;
        private double currentSpent;
        private double previousSpent;
        private double avg3MonthsSpent;
        private double deltaVsPrev;
        private double deltaVsPrevPct;
        private double deltaVsAvgPct;
        private boolean spike;
        private List<TopTxItem> topTransactions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsOverview {
        private String month;
        private String previousMonth;
        private int daysInMonth;
        private int daysElapsed;
        private int daysRemaining;
        private double timeElapsedPct;

        private double overallBudgetLimit;
        private double totalExpense;
        private double totalIncome;
        private double netSavings;
        private double budgetRemaining;
        private double safeToSpendPerDay;
        private double overallSpentPct;
        private double overallBurnRateRatio;
        private String overallBurnStatus; // SAFE | WARNING | DANGER | EXCEEDED | NO_BUDGET
        private String overallBurnMessage;

        private double previousMonthExpense;
        private double avg3MonthExpense;
        private double expenseDeltaVsPrevPct;

        private List<BurnRateItem> categoryBurnRates;
        private List<CategoryAnomaly> categoryAnomalies;
    }

    public AnalyticsOverview getOverviewForUser(User user, String monthParam) {
        YearMonth targetYm = resolveTargetYearMonth(monthParam);
        LocalDate today = LocalDate.now();
        List<Transaction> transactions = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(user.getId());
        List<Category> categories = categoryRepository.findByUserIsNullOrUserId(user.getId());
        double userOverallBudget = user.getMonthlyBudgetLimit() != null ? user.getMonthlyBudgetLimit() : 0.0;
        return computeOverview(targetYm, today, transactions, categories, userOverallBudget);
    }

    public AnalyticsOverview computeOverview(
            YearMonth targetYm,
            LocalDate referenceToday,
            List<Transaction> transactions,
            List<Category> categories,
            double userOverallBudget
    ) {
        if (transactions == null) transactions = List.of();
        if (categories == null) categories = List.of();

        int daysInMonth = targetYm.lengthOfMonth();
        YearMonth currentYm = YearMonth.from(referenceToday);
        int daysElapsed;
        if (targetYm.isBefore(currentYm)) {
            daysElapsed = daysInMonth;
        } else if (targetYm.isAfter(currentYm)) {
            daysElapsed = 1;
        } else {
            daysElapsed = Math.max(1, referenceToday.getDayOfMonth());
        }
        int daysRemaining = Math.max(1, daysInMonth - daysElapsed + 1);
        double timeElapsedPct = round1((daysElapsed * 100.0) / daysInMonth);

        YearMonth prevYm = targetYm.minusMonths(1);
        Set<YearMonth> last3Months = Set.of(targetYm.minusMonths(1), targetYm.minusMonths(2), targetYm.minusMonths(3));

        double totalIncome = 0.0;
        double totalExpense = 0.0;
        double prevMonthExpense = 0.0;
        double sum3MonthExpense = 0.0;

        Map<Long, Double> curSpentByCat = new HashMap<>();
        Map<Long, Double> prevSpentByCat = new HashMap<>();
        Map<Long, Double> last3SpentByCat = new HashMap<>();
        Map<Long, List<Transaction>> curTxByCat = new HashMap<>();
        Map<Long, Category> catMap = new HashMap<>();
        for (Category c : categories) {
            if (c.getId() != null) catMap.put(c.getId(), c);
        }

        for (Transaction tx : transactions) {
            if (tx.getTransactionDate() == null || tx.getAmount() == null) continue;
            YearMonth txYm = YearMonth.from(tx.getTransactionDate());
            double amt = tx.getAmount();
            boolean isExpense = "EXPENSE".equalsIgnoreCase(tx.getType());
            boolean isIncome = "INCOME".equalsIgnoreCase(tx.getType());

            if (txYm.equals(targetYm)) {
                if (isIncome) totalIncome += amt;
                if (isExpense) {
                    totalExpense += amt;
                    if (tx.getCategory() != null && tx.getCategory().getId() != null) {
                        Long cid = tx.getCategory().getId();
                        catMap.putIfAbsent(cid, tx.getCategory());
                        curSpentByCat.merge(cid, amt, Double::sum);
                        curTxByCat.computeIfAbsent(cid, k -> new ArrayList<>()).add(tx);
                    }
                }
            } else if (isExpense && txYm.equals(prevYm)) {
                prevMonthExpense += amt;
                if (tx.getCategory() != null && tx.getCategory().getId() != null) {
                    prevSpentByCat.merge(tx.getCategory().getId(), amt, Double::sum);
                }
            }

            if (isExpense && last3Months.contains(txYm)) {
                sum3MonthExpense += amt;
                if (tx.getCategory() != null && tx.getCategory().getId() != null) {
                    last3SpentByCat.merge(tx.getCategory().getId(), amt, Double::sum);
                }
            }
        }

        double sumCategoryBudgets = categories.stream()
                .filter(c -> "EXPENSE".equalsIgnoreCase(c.getType()) && c.getBudgetLimit() != null && c.getBudgetLimit() > 0)
                .mapToDouble(Category::getBudgetLimit)
                .sum();
        double overallBudgetLimit = userOverallBudget > 0 ? userOverallBudget : sumCategoryBudgets;

        double budgetRemaining = Math.max(0.0, overallBudgetLimit - totalExpense);
        double safeToSpendPerDay = overallBudgetLimit > 0 ? Math.floor(budgetRemaining / daysRemaining) : 0.0;
        double overallSpentPct = overallBudgetLimit > 0 ? round1((totalExpense * 100.0) / overallBudgetLimit) : 0.0;
        double overallBurnRateRatio = (overallBudgetLimit > 0 && timeElapsedPct > 0) ? round2(overallSpentPct / timeElapsedPct) : 0.0;

        String overallBurnStatus;
        String overallBurnMessage;
        if (overallBudgetLimit <= 0) {
            overallBurnStatus = "NO_BUDGET";
            overallBurnMessage = "Chưa thiết lập hạn mức ngân sách. Hãy đặt ngân sách tổng hoặc hạn mức từng danh mục để bật cảnh báo tốc độ tiêu dùng.";
        } else if (totalExpense > overallBudgetLimit) {
            overallBurnStatus = "EXCEEDED";
            overallBurnMessage = String.format("Đã vượt ngân sách %.1f%%! Bạn cần thắt chặt chi tiêu trong %d ngày còn lại.", overallSpentPct - 100.0, daysRemaining);
        } else if (overallBurnRateRatio >= 1.25 || (overallSpentPct - timeElapsedPct) >= 15.0) {
            overallBurnStatus = "DANGER";
            overallBurnMessage = String.format("Tốc độ tiêu dùng nhanh bất thường: mới qua %.0f%% thời gian tháng nhưng đã dùng %.1f%% ngân sách!", timeElapsedPct, overallSpentPct);
        } else if (overallBurnRateRatio >= 1.05 || overallSpentPct >= 80.0) {
            overallBurnStatus = "WARNING";
            overallBurnMessage = String.format("Chi tiêu đang nhỉnh hơn nhịp độ thời gian (%.1f%% so với %.0f%% tháng). Giữ mức tối đa %,.0f ₫/ngày.", overallSpentPct, timeElapsedPct, safeToSpendPerDay);
        } else {
            overallBurnStatus = "SAFE";
            overallBurnMessage = String.format("Tốc độ chi tiêu an toàn (%.1f%% ngân sách / %.0f%% thời gian tháng).", overallSpentPct, timeElapsedPct);
        }

        List<BurnRateItem> categoryBurnRates = new ArrayList<>();
        for (Category c : categories) {
            if (!"EXPENSE".equalsIgnoreCase(c.getType()) || c.getBudgetLimit() == null || c.getBudgetLimit() <= 0) continue;
            double limit = c.getBudgetLimit();
            double spent = curSpentByCat.getOrDefault(c.getId(), 0.0);
            double rem = Math.max(0.0, limit - spent);
            double safeDay = Math.floor(rem / daysRemaining);
            double sPct = round1((spent * 100.0) / limit);
            double ratio = timeElapsedPct > 0 ? round2(sPct / timeElapsedPct) : 0.0;

            String st;
            String msg;
            if (spent > limit) {
                st = "EXCEEDED";
                msg = String.format("Vượt hạn mức +%,.0f ₫", spent - limit);
            } else if (ratio >= 1.25 || (sPct - timeElapsedPct) >= 15.0) {
                st = "DANGER";
                msg = String.format("Tiêu nhanh gấp %.1fx nhịp tháng", ratio);
            } else if (ratio >= 1.05 || sPct >= 80.0) {
                st = "WARNING";
                msg = String.format("Cần giữ ≤ %,.0f ₫/ngày", safeDay);
            } else {
                st = "SAFE";
                msg = String.format("An toàn • Còn %,.0f ₫/ngày", safeDay);
            }

            categoryBurnRates.add(BurnRateItem.builder()
                    .categoryId(c.getId())
                    .categoryName(c.getName())
                    .color(c.getColor())
                    .budgetLimit(limit)
                    .spent(spent)
                    .remaining(rem)
                    .safeToSpendPerDay(safeDay)
                    .spentPct(sPct)
                    .timeElapsedPct(timeElapsedPct)
                    .burnRateRatio(ratio)
                    .status(st)
                    .message(msg)
                    .build());
        }
        categoryBurnRates.sort(Comparator.comparingDouble(BurnRateItem::getSpentPct).reversed());

        Set<Long> allCatIds = new HashSet<>();
        allCatIds.addAll(curSpentByCat.keySet());
        allCatIds.addAll(prevSpentByCat.keySet());

        List<CategoryAnomaly> anomalies = new ArrayList<>();
        for (Long cid : allCatIds) {
            double cur = curSpentByCat.getOrDefault(cid, 0.0);
            double prev = prevSpentByCat.getOrDefault(cid, 0.0);
            double avg3 = Math.round(last3SpentByCat.getOrDefault(cid, 0.0) / 3.0);
            double deltaPrev = cur - prev;
            double deltaPrevPct = prev > 0 ? round1((deltaPrev * 100.0) / prev) : (cur > 0 ? 100.0 : 0.0);
            double deltaAvgPct = avg3 > 0 ? round1(((cur - avg3) * 100.0) / avg3) : (cur > 0 ? 100.0 : 0.0);

            boolean isSpike = cur > 0 && ((prev > 0 && deltaPrevPct >= 20.0) || (avg3 > 0 && deltaAvgPct >= 25.0) || (prev == 0 && cur >= 500_000));

            List<TopTxItem> topTxs = curTxByCat.getOrDefault(cid, List.of()).stream()
                    .sorted(Comparator.comparingDouble((Transaction t) -> t.getAmount() != null ? t.getAmount() : 0.0).reversed())
                    .limit(3)
                    .map(t -> TopTxItem.builder()
                            .id(t.getId())
                            .description(t.getDescription() != null && !t.getDescription().isBlank() ? t.getDescription() : "Giao dịch")
                            .amount(t.getAmount() != null ? t.getAmount() : 0.0)
                            .date(t.getTransactionDate() != null ? t.getTransactionDate().toLocalDate().toString() : "")
                            .walletName(t.getWallet() != null ? t.getWallet().getName() : "")
                            .build())
                    .collect(Collectors.toList());

            Category cat = catMap.get(cid);
            anomalies.add(CategoryAnomaly.builder()
                    .categoryId(cid)
                    .categoryName(cat != null ? cat.getName() : "Danh mục #" + cid)
                    .color(cat != null ? cat.getColor() : "#6366f1")
                    .currentSpent(cur)
                    .previousSpent(prev)
                    .avg3MonthsSpent(avg3)
                    .deltaVsPrev(deltaPrev)
                    .deltaVsPrevPct(deltaPrevPct)
                    .deltaVsAvgPct(deltaAvgPct)
                    .spike(isSpike)
                    .topTransactions(topTxs)
                    .build());
        }
        anomalies.sort(Comparator.comparing(CategoryAnomaly::isSpike).reversed()
                .thenComparing(Comparator.comparingDouble(CategoryAnomaly::getDeltaVsPrev).reversed()));

        double avg3MonthExpense = Math.round(sum3MonthExpense / 3.0);
        double expenseDeltaVsPrevPct = prevMonthExpense > 0
                ? round1(((totalExpense - prevMonthExpense) * 100.0) / prevMonthExpense)
                : (totalExpense > 0 ? 100.0 : 0.0);

        return AnalyticsOverview.builder()
                .month(targetYm.toString())
                .previousMonth(prevYm.toString())
                .daysInMonth(daysInMonth)
                .daysElapsed(daysElapsed)
                .daysRemaining(daysRemaining)
                .timeElapsedPct(timeElapsedPct)
                .overallBudgetLimit(overallBudgetLimit)
                .totalExpense(totalExpense)
                .totalIncome(totalIncome)
                .netSavings(totalIncome - totalExpense)
                .budgetRemaining(budgetRemaining)
                .safeToSpendPerDay(safeToSpendPerDay)
                .overallSpentPct(overallSpentPct)
                .overallBurnRateRatio(overallBurnRateRatio)
                .overallBurnStatus(overallBurnStatus)
                .overallBurnMessage(overallBurnMessage)
                .previousMonthExpense(prevMonthExpense)
                .avg3MonthExpense(avg3MonthExpense)
                .expenseDeltaVsPrevPct(expenseDeltaVsPrevPct)
                .categoryBurnRates(categoryBurnRates)
                .categoryAnomalies(anomalies)
                .build();
    }

    private YearMonth resolveTargetYearMonth(String monthParam) {
        if (monthParam != null && monthParam.matches("\\d{4}-\\d{2}")) {
            try {
                return YearMonth.parse(monthParam);
            } catch (Exception ignored) {}
        }
        return YearMonth.now();
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
