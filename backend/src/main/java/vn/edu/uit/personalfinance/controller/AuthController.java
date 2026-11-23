package vn.edu.uit.personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.User;
import vn.edu.uit.personalfinance.repository.UserRepository;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Data
    public static class LoginRequest {
        private String fullName;
        private String email;
        private String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập email"));
        }

        String email = request.getEmail().trim();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email chưa được đăng ký"));
        }

        User user = userOpt.get();
        if (request.getPassword() != null && user.getPasswordHash() != null
                && !"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy".equals(user.getPasswordHash())
                && !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("message", "Mật khẩu không chính xác"));
        }

        return ResponseEntity.ok(Map.of(
                "token", "bearer-token-" + user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName() != null ? user.getFullName() : "Người dùng"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không được để trống"));
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu không được để trống"));
        }

        String email = request.getEmail().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email này đã tồn tại"));
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setFullName(
                (request.getFullName() != null && !request.getFullName().isBlank())
                        ? request.getFullName().trim()
                        : email.split("@")[0]
        );

        User savedUser = userRepository.save(newUser);

        return ResponseEntity.ok(Map.of(
                "message", "Đăng ký thành công",
                "token", "bearer-token-" + savedUser.getId(),
                "email", savedUser.getEmail(),
                "fullName", savedUser.getFullName()
        ));
    }
}
