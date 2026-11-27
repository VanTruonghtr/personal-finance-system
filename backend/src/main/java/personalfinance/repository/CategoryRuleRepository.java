package personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personalfinance.entity.CategoryRule;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {
    List<CategoryRule> findByUserIdOrderByPriorityDescIdDesc(Long userId);
    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);
}
