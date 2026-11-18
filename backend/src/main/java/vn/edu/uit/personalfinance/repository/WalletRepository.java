package vn.edu.uit.personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.uit.personalfinance.entity.Wallet;
import java.util.List;
import java.util.Optional;


public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findByUserId(Long userId);
    Optional<Wallet> findByIdAndUserId(Long id, Long userId);
}