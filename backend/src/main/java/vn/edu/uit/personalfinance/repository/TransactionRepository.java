package vn.edu.uit.personalfinance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.uit.personalfinance.entity.Transaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);
    List<Transaction> findByUserIdOrderByTransactionDateDescIdDesc(Long userId);
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND (:walletId IS NULL OR t.wallet.id = :walletId) " +
           "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:startDate IS NULL OR t.transactionDate >= :startDate) " +
           "AND (:endDate IS NULL OR t.transactionDate <= :endDate) " +
           "AND (:keyword IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Transaction> searchTransactions(
        @Param("userId") Long userId,
        @Param("walletId") Long walletId,
        @Param("categoryId") Long categoryId,
        @Param("type") String type,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}