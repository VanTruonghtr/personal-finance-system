package vn.edu.uit.personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.Transaction;
import vn.edu.uit.personalfinance.repository.TransactionRepository;
import vn.edu.uit.personalfinance.service.TransactionService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        List<Transaction> all = transactionRepository.findAll();
        List<Transaction> userTx = all.stream()
                .filter(t -> t.getUser() != null && t.getUser().getId() == 9L)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userTx.isEmpty() ? all : userTx);
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
        return ResponseEntity.ok(transactionService.createTransaction(
                req.getWalletId(), req.getTargetWalletId(), req.getCategoryId(), req.getType(), req.getAmount(), req.getDescription(), 9L));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id, @RequestBody TxRequest req) {
        return ResponseEntity.ok(transactionService.updateTransaction(
                id, req.getWalletId(), req.getTargetWalletId(), req.getCategoryId(), req.getType(), req.getAmount(), req.getDescription()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }
}