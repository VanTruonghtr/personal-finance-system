package personalfinance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalfinance.entity.Category;
import personalfinance.entity.CategoryRule;
import personalfinance.entity.Wallet;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.CategoryRuleRepository;
import personalfinance.repository.WalletRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QuickParseServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryRuleRepository categoryRuleRepository;

    @InjectMocks private QuickParseService quickParseService;

    private List<Wallet> wallets;
    private List<Category> categories;
    private List<CategoryRule> rules;

    @BeforeEach
    void setUp() {
        wallets = List.of(
                Wallet.builder().id(1L).name("Tiền mặt").type("CASH").balance(1_000_000.0).build(),
                Wallet.builder().id(2L).name("Momo").type("EWALLET").balance(2_000_000.0).build(),
                Wallet.builder().id(3L).name("Vietcombank").type("BANK").balance(10_000_000.0).build()
        );
        Category food = Category.builder().id(10L).name("Ăn uống").type("EXPENSE").color("#f97316").build();
        Category transport = Category.builder().id(11L).name("Di chuyển").type("EXPENSE").color("#3b82f6").build();
        Category salary = Category.builder().id(12L).name("Lương").type("INCOME").color("#10b981").build();
        categories = List.of(food, transport, salary);

        rules = List.of(
                CategoryRule.builder().id(100L).keyword("Grab").category(transport).priority(10).build(),
                CategoryRule.builder().id(101L).keyword("Xăng").category(transport).priority(5).build()
        );
    }

    @Test
    void shouldParseExpenseWithKUnitAndWalletName() {
        QuickParseService.QuickParseResult res = quickParseService.parse("Ăn trưa 45k ví Momo", wallets, categories, rules);
        assertTrue(res.isValid());
        assertEquals("EXPENSE", res.getType());
        assertEquals(45_000.0, res.getAmount());
        assertEquals(2L, res.getWalletId());
        assertEquals(10L, res.getCategoryId());
        assertEquals("Ăn trưa", res.getDescription());
    }

    @Test
    void shouldParseIncomeWithMillionUnitAndBankAlias() {
        QuickParseService.QuickParseResult res = quickParseService.parse("Lương tháng 11 15.5tr VCB", wallets, categories, rules);
        assertTrue(res.isValid());
        assertEquals("INCOME", res.getType());
        assertEquals(15_500_000.0, res.getAmount());
        assertEquals(3L, res.getWalletId());
        assertEquals(12L, res.getCategoryId());
    }

    @Test
    void shouldPrioritizeUserKeywordRule() {
        QuickParseService.QuickParseResult res = quickParseService.parse("Đổ xăng xe 120.5k tiền mặt", wallets, categories, rules);
        assertTrue(res.isValid());
        assertEquals(120_500.0, res.getAmount());
        assertEquals(1L, res.getWalletId());
        assertEquals(11L, res.getCategoryId());
        assertNotNull(res.getMatchedRule());
        assertTrue(res.getMatchedRule().contains("Xăng"));
    }

    @Test
    void shouldRejectInputWithoutAmount() {
        QuickParseService.QuickParseResult res = quickParseService.parse("Đi cà phê với bạn", wallets, categories, rules);
        assertFalse(res.isValid());
        assertNotNull(res.getError());
    }
}
