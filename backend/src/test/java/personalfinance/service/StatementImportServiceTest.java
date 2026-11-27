package personalfinance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalfinance.entity.Category;
import personalfinance.entity.CategoryRule;
import personalfinance.entity.Transaction;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.CategoryRuleRepository;
import personalfinance.repository.TransactionRepository;
import personalfinance.repository.WalletRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryRuleRepository categoryRuleRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private TransactionService transactionService;

    private StatementImportService statementImportService;

    @BeforeEach
    void setUp() {
        QuickParseService quickParseService = new QuickParseService(walletRepository, categoryRepository, categoryRuleRepository);
        statementImportService = new StatementImportService(
                transactionRepository, categoryRepository, categoryRuleRepository, quickParseService, transactionService
        );
    }

    @Test
    void shouldDetectDatabaseAndInBatchDuplicatesAndAutoCategorize() {
        Category transport = Category.builder().id(11L).name("Di chuyển").type("EXPENSE").color("#3b82f6").build();
        CategoryRule grabRule = CategoryRule.builder().id(1L).keyword("Grab").category(transport).priority(10).build();

        Transaction existingTx = Transaction.builder()
                .id(55L)
                .type("EXPENSE")
                .amount(65_000.0)
                .description("GrabBike đi học")
                .transactionDate(LocalDateTime.of(2026, 11, 20, 8, 30))
                .build();

        List<StatementImportService.ImportRowRequest> rows = List.of(
                StatementImportService.ImportRowRequest.builder()
                        .date("2026-11-20").amount(65_000.0).type("EXPENSE").description("GrabBike đi học").build(),
                StatementImportService.ImportRowRequest.builder()
                        .date("2026-11-21").amount(48_000.0).type("EXPENSE").description("GrabCar về nhà").build(),
                StatementImportService.ImportRowRequest.builder()
                        .date("2026-11-21").amount(48_000.0).type("EXPENSE").description("GrabCar về nhà").build()
        );

        StatementImportService.ImportPreviewResponse res = statementImportService.buildPreview(
                rows, 1L, List.of(existingTx), List.of(transport), List.of(grabRule)
        );

        assertEquals(3, res.getTotalRows());
        assertEquals(1, res.getValidRows());
        assertEquals(2, res.getDuplicateRows());

        assertTrue(res.getRows().get(0).isDuplicate());
        assertFalse(res.getRows().get(1).isDuplicate());
        assertEquals(11L, res.getRows().get(1).getCategoryId());
        assertTrue(res.getRows().get(2).isDuplicate());
    }
}
