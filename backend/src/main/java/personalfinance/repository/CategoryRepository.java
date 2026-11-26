package personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personalfinance.entity.Category;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIsNullOrUserId(Long userId);
    boolean existsByUserIsNull();
}