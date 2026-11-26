package personalfinance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import personalfinance.entity.Category;
import personalfinance.repository.CategoryRepository;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (!categoryRepository.existsByUserIsNull()) {
            List<Category> defaults = List.of(
                Category.builder().name("Tiền lương").type("INCOME").icon("briefcase").color("#10B981").build(),
                Category.builder().name("Tiền thưởng").type("INCOME").icon("gift").color("#059669").build(),
                Category.builder().name("Thu nhập khác").type("INCOME").icon("plus-circle").color("#34D399").build(),
                Category.builder().name("Ăn uống").type("EXPENSE").icon("utensils").color("#F59E0B").build(),
                Category.builder().name("Di chuyển").type("EXPENSE").icon("car").color("#3B82F6").build(),
                Category.builder().name("Nhà ở & Hóa đơn").type("EXPENSE").icon("home").color("#6366F1").build(),
                Category.builder().name("Mua sắm").type("EXPENSE").icon("shopping-bag").color("#EC4899").build(),
                Category.builder().name("Học tập & Sức khỏe").type("EXPENSE").icon("book-open").color("#8B5CF6").build(),
                Category.builder().name("Giải trí").type("EXPENSE").icon("film").color("#F43F5E").build(),
                Category.builder().name("Chi tiêu khác").type("EXPENSE").icon("more-horizontal").color("#6B7280").build()
            );
            categoryRepository.saveAll(defaults);
        }
    }
}