package personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personalfinance.entity.RecurringTransaction;
import java.util.List;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserId(Long userId);
}