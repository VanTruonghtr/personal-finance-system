package vn.edu.uit.personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uit.personalfinance.entity.RecurringTransaction;
import java.util.List;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserId(Long userId);
}