package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.SavingsGoal;
import personalfinance.entity.Transaction;
import personalfinance.entity.User;
import personalfinance.entity.Wallet;
import personalfinance.repository.SavingsGoalRepository;
import personalfinance.repository.TransactionRepository;
import personalfinance.repository.WalletRepository;
import personalfinance.security.CurrentUserService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalRepository goalRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<SavingsGoal>> getAllGoals() {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(goalRepository.findByUserId(userId));
    }

    @Data
    public static class GoalRequest {
        private String name;
        private Double targetAmount;
        private Double currentAmount;
        private String deadline;
        private String color;
    }

    @PostMapping
    public ResponseEntity<?> createGoal(@RequestBody GoalRequest req) {
        User owner = currentUserService.getCurrentUser();
        SavingsGoal goal = SavingsGoal.builder()
                .name(req.getName())
                .targetAmount(req.getTargetAmount() != null ? req.getTargetAmount() : 0.0)
                .currentAmount(req.getCurrentAmount() != null ? req.getCurrentAmount() : 0.0)
                .deadline(req.getDeadline() != null && !req.getDeadline().isBlank() ? LocalDate.parse(req.getDeadline()) : null)
                .color(req.getColor() != null ? req.getColor() : "#10b981")
                .user(owner)
                .build();
        return ResponseEntity.ok(goalRepository.save(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGoal(@PathVariable Long id, @RequestBody GoalRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        SavingsGoal goal = goalRepository.findById(id)
                .filter(g -> g.getUser() != null && g.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tiêu"));
        if (req.getName() != null) goal.setName(req.getName());
        if (req.getTargetAmount() != null) goal.setTargetAmount(req.getTargetAmount());
        if (req.getCurrentAmount() != null) goal.setCurrentAmount(req.getCurrentAmount());
        if (req.getDeadline() != null) {
            goal.setDeadline(req.getDeadline().isBlank() ? null : LocalDate.parse(req.getDeadline()));
        }
        if (req.getColor() != null) goal.setColor(req.getColor());
        return ResponseEntity.ok(goalRepository.save(goal));
    }

    @Data
    public static class DepositRequest {
        private Long walletId;
        private Double amount;
        private String action; // DEPOSIT hoặc WITHDRAW
    }

    @PostMapping("/{id}/contribute")
    @Transactional
    public ResponseEntity<?> contributeGoal(@PathVariable Long id, @RequestBody DepositRequest req) {
        try {
            User owner = currentUserService.getCurrentUser();
            SavingsGoal goal = goalRepository.findById(id)
                    .filter(g -> g.getUser() != null && g.getUser().getId().equals(owner.getId()))
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tiêu"));
            Wallet wallet = walletRepository.findByIdAndUserId(req.getWalletId(), owner.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ví"));

            double val = req.getAmount() != null ? req.getAmount() : 0.0;
            if (val <= 0) return ResponseEntity.badRequest().body(Map.of("message", "Số tiền phải lớn hơn 0"));

            boolean isWithdraw = "WITHDRAW".equalsIgnoreCase(req.getAction());
            if (isWithdraw) {
                if (goal.getCurrentAmount() < val) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Số dư trong mục tiêu không đủ để rút!"));
                }
                goal.setCurrentAmount(goal.getCurrentAmount() - val);
                wallet.setBalance((wallet.getBalance() != null ? wallet.getBalance() : 0.0) + val);
            } else {
                goal.setCurrentAmount(goal.getCurrentAmount() + val);
                wallet.setBalance((wallet.getBalance() != null ? wallet.getBalance() : 0.0) - val);
            }

            walletRepository.save(wallet);
            goalRepository.save(goal);

            Transaction tx = Transaction.builder()
                    .type(isWithdraw ? "INCOME" : "EXPENSE")
                    .amount(val)
                    .description((isWithdraw ? "Rút từ mục tiêu: " : "Nạp vào mục tiêu: ") + goal.getName())
                    .transactionDate(LocalDateTime.now())
                    .wallet(wallet)
                    .user(owner)
                    .build();
            transactionRepository.save(tx);

            return ResponseEntity.ok(goal);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGoal(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserId();
        SavingsGoal goal = goalRepository.findById(id)
                .filter(g -> g.getUser() != null && g.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tiêu"));
        goalRepository.delete(goal);
        return ResponseEntity.ok().build();
    }
}