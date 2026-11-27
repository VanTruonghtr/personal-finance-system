package personalfinance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalfinance.entity.Category;
import personalfinance.entity.Transaction;
import personalfinance.entity.Wallet;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.TransactionRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private AnalyticsService analyticsService;

    @Test
    void shouldCalculateSafeToSpendAndDetectHighBurnRate() {
        Category food = Category.builder().id(1L).name("Ăn uống").type("EXPENSE").budgetLimit(5_000_000.0).color("#f97316").build();
        Wallet vcb = Wallet.builder().id(1L).name("VCB").type("BANK").build();

        Transaction tx1 = Transaction.builder()
                .id(101L).type("EXPENSE").amount(3_000_000.0).description("Tiệc sinh nhật")
                .transactionDate(LocalDateTime.of(2026, 11, 5, 19, 0))
                .category(food).wallet(vcb).build();

        AnalyticsService.AnalyticsOverview overview = analyticsService.computeOverview(
                YearMonth.of(2026, 11),
                LocalDate.of(2026, 11, 10),
                List.of(tx1),
                List.of(food),
                5_000_000.0
        );

        assertEquals(30, overview.getDaysInMonth());
        assertEquals(10, overview.getDaysElapsed());
        assertEquals(21, overview.getDaysRemaining());
        assertEquals(2_000_000.0, overview.getBudgetRemaining());
        assertEquals(Math.floor(2_000_000.0 / 21), overview.getSafeToSpendPerDay());
        assertEquals("DANGER", overview.getOverallBurnStatus());
        assertEquals(1, overview.getCategoryBurnRates().size());
        assertEquals("DANGER", overview.getCategoryBurnRates().get(0).getStatus());
    }

    @Test
    void shouldDetectCategorySpikeAndIncludeTopTransactions() {
        Category shopping = Category.builder().id(2L).name("Mua sắm").type("EXPENSE").budgetLimit(2_000_000.0).color("#ec4899").build();
        Wallet momo = Wallet.builder().id(2L).name("Momo").type("EWALLET").build();

        Transaction prevTx = Transaction.builder()
                .id(1L).type("EXPENSE").amount(1_000_000.0).description("Mua áo")
                .transactionDate(LocalDateTime.of(2026, 10, 12, 10, 0))
                .category(shopping).wallet(momo).build();

        Transaction curTx1 = Transaction.builder()
                .id(2L).type("EXPENSE").amount(2_200_000.0).description("Mua tai nghe")
                .transactionDate(LocalDateTime.of(2026, 11, 8, 14, 0))
                .category(shopping).wallet(momo).build();

        Transaction curTx2 = Transaction.builder()
                .id(3L).type("EXPENSE").amount(500_000.0).description("Shopee")
                .transactionDate(LocalDateTime.of(2026, 11, 9, 15, 0))
                .category(shopping).wallet(momo).build();

        AnalyticsService.AnalyticsOverview overview = analyticsService.computeOverview(
                YearMonth.of(2026, 11),
                LocalDate.of(2026, 11, 20),
                List.of(prevTx, curTx1, curTx2),
                List.of(shopping),
                0.0
        );

        assertEquals(1, overview.getCategoryAnomalies().size());
        AnalyticsService.CategoryAnomaly anomaly = overview.getCategoryAnomalies().get(0);
        assertTrue(anomaly.isSpike());
        assertEquals(2_700_000.0, anomaly.getCurrentSpent());
        assertEquals(1_000_000.0, anomaly.getPreviousSpent());
        assertEquals(170.0, anomaly.getDeltaVsPrevPct());
        assertEquals(2, anomaly.getTopTransactions().size());
        assertEquals("Mua tai nghe", anomaly.getTopTransactions().get(0).getDescription());
    }
}
