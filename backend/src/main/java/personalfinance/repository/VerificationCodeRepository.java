package personalfinance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personalfinance.entity.VerificationCode;

import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(String email, String purpose);
    List<VerificationCode> findByEmailAndPurposeAndUsedFalse(String email, String purpose);
}