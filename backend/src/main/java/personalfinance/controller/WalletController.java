package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.User;
import personalfinance.entity.Wallet;
import personalfinance.repository.WalletRepository;
import personalfinance.security.CurrentUserService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletRepository walletRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<Wallet>> getAllWallets() {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(walletRepository.findByUserId(userId));
    }

    @Data
    public static class WalletRequest {
        private String name;
        private Double balance;
        private String type;
        private String status;
    }

    @PostMapping
    public ResponseEntity<?> createWallet(@RequestBody WalletRequest request) {
        User owner = currentUserService.getCurrentUser();
        Wallet wallet = Wallet.builder()
                .name(request.getName() != null ? request.getName() : "Ví mới")
                .balance(request.getBalance() != null ? request.getBalance() : 0.0)
                .type(request.getType() != null ? request.getType() : "BANK")
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .createdAt(LocalDateTime.now())
                .user(owner)
                .build();

        return ResponseEntity.ok(walletRepository.save(wallet));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateWallet(@PathVariable Long id, @RequestBody WalletRequest request) {
        Long userId = currentUserService.getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví"));

        if (request.getName() != null) wallet.setName(request.getName());
        if (request.getType() != null) wallet.setType(request.getType());
        if (request.getBalance() != null) wallet.setBalance(request.getBalance());
        if (request.getStatus() != null) wallet.setStatus(request.getStatus());

        return ResponseEntity.ok(walletRepository.save(wallet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWallet(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserId();
        Wallet wallet = walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví"));
        walletRepository.delete(wallet);
        return ResponseEntity.ok().build();
    }
}