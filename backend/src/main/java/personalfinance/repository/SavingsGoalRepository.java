package personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personalfinance.entity.SavingsGoal;
import java.util.List;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUserId(Long userId);
}