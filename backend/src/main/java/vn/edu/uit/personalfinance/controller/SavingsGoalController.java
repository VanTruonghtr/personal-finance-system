package vn.edu.uit.personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.SavingsGoal;
import vn.edu.uit.personalfinance.entity.Transaction;
import vn.edu.uit.personalfinance.entity.User;
import vn.edu.uit.personalfinance.entity.Wallet;
import vn.edu.uit.personalfinance.repository.SavingsGoalRepository;
import vn.edu.uit.personalfinance.repository.TransactionRepository;
import vn.edu.uit.personalfinance.repository.UserRepository;
import vn.edu.uit.personalfinance.repository.WalletRepository;

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
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SavingsGoal>> getAllGoals() {
        List<SavingsGoal> list = goalRepository.findByUserId(9L);
        return ResponseEntity.ok(list.isEmpty() ? goalRepository.findAll() : list);
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
        User owner = userRepository.findById(9L).orElse(null);
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
        SavingsGoal goal = goalRepository.findById(id)
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
            SavingsGoal goal = goalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tiêu"));
            Wallet wallet = walletRepository.findById(req.getWalletId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy ví"));
            User owner = userRepository.findById(9L).orElse(null);

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

            // Tự động ghi nhận 1 giao dịch vào lịch sử
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
        goalRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}