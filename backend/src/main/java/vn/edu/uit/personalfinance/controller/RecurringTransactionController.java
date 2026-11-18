package vn.edu.uit.personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.*;
import vn.edu.uit.personalfinance.repository.*;
import vn.edu.uit.personalfinance.service.TransactionService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringTransactionController {

    private final RecurringTransactionRepository recurringRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<RecurringTransaction>> getAll() {
        List<RecurringTransaction> list = recurringRepository.findByUserId(9L);
        return ResponseEntity.ok(list.isEmpty() ? recurringRepository.findAll() : list);
    }

    @Data
    public static class RecurringRequest {
        private String title;
        private Double amount;
        private String type;
        private String frequency;
        private String nextDueDate;
        private Long walletId;
        private Long categoryId;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RecurringRequest req) {
        try {
            User owner = userRepository.findById(9L).orElse(null);
            Wallet wallet = req.getWalletId() != null ? walletRepository.findById(req.getWalletId()).orElse(null) : null;
            Category cat = (req.getCategoryId() != null && req.getCategoryId() > 0)
                    ? categoryRepository.findById(req.getCategoryId()).orElse(null) : null;

            RecurringTransaction item = RecurringTransaction.builder()
                    .title(req.getTitle())
                    .amount(req.getAmount() != null ? req.getAmount() : 0.0)
                    .type(req.getType() != null ? req.getType() : "EXPENSE")
                    .frequency(req.getFrequency() != null ? req.getFrequency() : "MONTHLY")
                    .nextDueDate(req.getNextDueDate() != null && !req.getNextDueDate().isBlank()
                            ? LocalDate.parse(req.getNextDueDate()) : LocalDate.now())
                    .active(true)
                    .wallet(wallet)
                    .category(cat)
                    .user(owner)
                    .build();
            return ResponseEntity.ok(recurringRepository.save(item));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody RecurringRequest req) {
        RecurringTransaction item = recurringRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khoản định kỳ"));
        if (req.getTitle() != null) item.setTitle(req.getTitle());
        if (req.getAmount() != null) item.setAmount(req.getAmount());
        if (req.getType() != null) item.setType(req.getType());
        if (req.getFrequency() != null) item.setFrequency(req.getFrequency());
        if (req.getNextDueDate() != null && !req.getNextDueDate().isBlank()) {
            item.setNextDueDate(LocalDate.parse(req.getNextDueDate()));
        }
        if (req.getWalletId() != null) {
            walletRepository.findById(req.getWalletId()).ifPresent(item::setWallet);
        }
        if (req.getCategoryId() != null) {
            item.setCategory(req.getCategoryId() > 0 ? categoryRepository.findById(req.getCategoryId()).orElse(null) : null);
        }
        return ResponseEntity.ok(recurringRepository.save(item));
    }

    @PostMapping("/{id}/execute")
    @Transactional
    public ResponseEntity<?> executeNow(@PathVariable Long id) {
        try {
            RecurringTransaction item = recurringRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy khoản định kỳ"));
            if (item.getWallet() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Khoản định kỳ này chưa chọn ví thanh toán!"));
            }

            // 1. Tạo giao dịch thật và cập nhật số dư ví
            Long catId = item.getCategory() != null ? item.getCategory().getId() : null;
            transactionService.createTransaction(
                    item.getWallet().getId(),
                    null,
                    catId,
                    item.getType(),
                    item.getAmount(),
                    "[Định kỳ] " + item.getTitle(),
                    9L
            );

            // 2. Tự động tính ngày đến hạn tiếp theo
            LocalDate current = item.getNextDueDate() != null ? item.getNextDueDate() : LocalDate.now();
            LocalDate next = switch (item.getFrequency()) {
                case "DAILY" -> current.plusDays(1);
                case "WEEKLY" -> current.plusWeeks(1);
                case "YEARLY" -> current.plusYears(1);
                default -> current.plusMonths(1);
            };
            item.setNextDueDate(next);
            return ResponseEntity.ok(recurringRepository.save(item));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        recurringRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}