package vn.edu.uit.personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uit.personalfinance.entity.SavingsGoal;
import java.util.List;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUserId(Long userId);
}