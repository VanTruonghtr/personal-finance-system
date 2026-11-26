package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.Transaction;
import personalfinance.security.CurrentUserService;
import personalfinance.service.TransactionService;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(transactionService.getTransactionsByUser(userId));
    }

    @Data
    public static class TxRequest {
        private Long walletId;
        private Long targetWalletId;
        private Long categoryId;
        private String type;
        private Double amount;
        private String description;
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody TxRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(transactionService.createTransaction(
                req.getWalletId(), req.getTargetWalletId(), req.getCategoryId(),
                req.getType(), req.getAmount(), req.getDescription(), userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id, @RequestBody TxRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(transactionService.updateTransaction(
                id, req.getWalletId(), req.getTargetWalletId(), req.getCategoryId(),
                req.getType(), req.getAmount(), req.getDescription(), userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserId();
        transactionService.deleteTransaction(id, userId);
        return ResponseEntity.ok().build();
    }
}