package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.Transaction;
import personalfinance.security.CurrentUserService;
import personalfinance.service.QuickParseService;
import personalfinance.service.StatementImportService;
import personalfinance.service.TransactionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final QuickParseService quickParseService;
    private final StatementImportService statementImportService;
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

    @Data
    public static class QuickParseRequest {
        private String text;
        private Long walletId;
        private Long categoryId;
    }

    @Data
    public static class ImportBatchRequest {
        private Long defaultWalletId;
        private Boolean skipDuplicates;
        private List<StatementImportService.ImportRowRequest> rows;
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody TxRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(transactionService.createTransaction(
                req.getWalletId(), req.getTargetWalletId(), req.getCategoryId(),
                req.getType(), req.getAmount(), req.getDescription(), userId));
    }

    @PostMapping("/quick-parse/preview")
    public ResponseEntity<QuickParseService.QuickParseResult> previewQuickParse(@RequestBody QuickParseRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(quickParseService.parseForUser(req.getText(), userId));
    }

    @PostMapping("/quick-parse")
    public ResponseEntity<?> createQuickParsedTransaction(@RequestBody QuickParseRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        QuickParseService.QuickParseResult parsed = quickParseService.parseForUser(req.getText(), userId);
        if (!parsed.isValid()) {
            return ResponseEntity.badRequest().body(Map.of("message", parsed.getError()));
        }
        Long walletId = req.getWalletId() != null ? req.getWalletId() : parsed.getWalletId();
        if (walletId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bạn cần tạo ít nhất 1 ví trước khi ghi giao dịch nhanh"));
        }
        Long categoryId = req.getCategoryId() != null ? req.getCategoryId() : parsed.getCategoryId();
        Transaction created = transactionService.createTransaction(
                walletId,
                null,
                categoryId,
                parsed.getType(),
                parsed.getAmount(),
                parsed.getDescription(),
                userId
        );
        return ResponseEntity.ok(Map.of(
                "transaction", created,
                "parsed", parsed
        ));
    }

    @PostMapping("/import/preview")
    public ResponseEntity<StatementImportService.ImportPreviewResponse> previewImport(@RequestBody ImportBatchRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(statementImportService.preview(req.getRows(), req.getDefaultWalletId(), userId));
    }

    @PostMapping("/import/commit")
    public ResponseEntity<?> commitImport(@RequestBody ImportBatchRequest req) {
        Long userId = currentUserService.getCurrentUserId();
        boolean skipDup = req.getSkipDuplicates() == null || req.getSkipDuplicates();
        if (req.getDefaultWalletId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn ví đích để nhập sao kê"));
        }
        return ResponseEntity.ok(statementImportService.commit(req.getRows(), req.getDefaultWalletId(), skipDup, userId));
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
