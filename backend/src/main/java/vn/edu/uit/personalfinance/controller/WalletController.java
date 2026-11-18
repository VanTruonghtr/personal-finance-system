package vn.edu.uit.personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.User;
import vn.edu.uit.personalfinance.entity.Wallet;
import vn.edu.uit.personalfinance.repository.UserRepository;
import vn.edu.uit.personalfinance.repository.WalletRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Wallet>> getAllWallets() {
        List<Wallet> wallets = walletRepository.findByUserId(9L);
        return ResponseEntity.ok(wallets.isEmpty() ? walletRepository.findAll() : wallets);
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
        User owner = userRepository.findById(9L).orElseGet(() -> 
            userRepository.findAll().stream().findFirst().orElseThrow(() -> new RuntimeException("Không tìm thấy user"))
        );

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
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví"));

        if (request.getName() != null) wallet.setName(request.getName());
        if (request.getType() != null) wallet.setType(request.getType());
        if (request.getBalance() != null) wallet.setBalance(request.getBalance());
        if (request.getStatus() != null) wallet.setStatus(request.getStatus());

        return ResponseEntity.ok(walletRepository.save(wallet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWallet(@PathVariable Long id) {
        walletRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}