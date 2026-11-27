package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import personalfinance.entity.User;
import personalfinance.entity.VerificationCode;
import personalfinance.repository.UserRepository;
import personalfinance.repository.VerificationCodeRepository;
import personalfinance.security.CurrentUserService;
import personalfinance.security.JwtService;
import personalfinance.service.EmailService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String LEGACY_DEMO_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Data
    public static class LoginRequest {
        private String fullName;
        private String email;
        private String password;
        private String otp;
    }

    @Data
    public static class ResetPasswordRequest {
        private String email;
        private String otp;
        private String newPassword;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }

    @Data
    public static class GoogleLoginRequest {
        private String credential;
    }

    private boolean matchesPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        if (LEGACY_DEMO_HASH.equals(storedHash) && "123456".equals(rawPassword)) return true;
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    private Map<String, Object> buildAuthResponse(User user, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (message != null) body.put("message", message);
        body.put("token", jwtService.generateToken(user.getEmail()));
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("fullName", user.getFullName() != null ? user.getFullName() : "Người dùng");
        body.put("role", user.getRole() != null ? user.getRole() : "USER");
        body.put("monthlyBudgetLimit", user.getMonthlyBudgetLimit() != null ? user.getMonthlyBudgetLimit() : 0.0);
        return body;
    }

    private String createAndSaveOtp(String email, String purpose) {
        List<VerificationCode> oldCodes = codeRepository.findByEmailAndPurposeAndUsedFalse(email, purpose);
        if (!oldCodes.isEmpty()) {
            oldCodes.forEach(c -> c.setUsed(true));
            codeRepository.saveAll(oldCodes);
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        VerificationCode vc = VerificationCode.builder()
                .email(email)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();
        codeRepository.save(vc);
        return code;
    }

    private String verifyOtp(String email, String purpose, String inputOtp) {
        if (inputOtp == null || inputOtp.isBlank()) {
            return "Vui lòng nhập mã xác nhận 6 số";
        }
        Optional<VerificationCode> opt = codeRepository.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email, purpose);
        if (opt.isEmpty()) {
            return "Mã xác nhận không tồn tại hoặc đã được sử dụng";
        }
        VerificationCode vc = opt.get();
        if (vc.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "Mã xác nhận đã hết hạn. Vui lòng yêu cầu gửi lại mã mới";
        }
        if (!vc.getCode().equals(inputOtp.trim())) {
            return "Mã xác nhận không chính xác";
        }
        vc.setUsed(true);
        codeRepository.save(vc);
        return null;
    }

    // 1. ĐĂNG NHẬP THƯỜNG
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập email"));
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập mật khẩu"));
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email chưa được đăng ký"));
        }

        User user = userOpt.get();
        if (!matchesPassword(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("message", "Mật khẩu không chính xác"));
        }

        return ResponseEntity.ok(buildAuthResponse(user, null));
    }

    // 2. ĐĂNG KÝ BƯỚC 1: GỬI MÃ OTP QUA EMAIL
    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegisterOtp(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không được để trống"));
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu phải có ít nhất 6 ký tự"));
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email này đã được đăng ký"));
        }

        try {
            String otp = createAndSaveOtp(email, "REGISTER");
            boolean sent = emailService.sendOtpEmail(email, otp, "REGISTER");
            String msg = sent
                    ? "Đã gửi mã xác nhận 6 số tới email " + email + " (hiệu lực 10 phút)."
                    : "Chưa cấu hình SMTP/Brevo — vui lòng xem mã OTP trên cửa sổ Console Backend để test.";
            return ResponseEntity.ok(Map.of("message", msg, "emailSent", sent));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không gửi được email xác nhận: " + e.getMessage()));
        }
    }

    // 3. ĐĂNG KÝ BƯỚC 2: XÁC NHẬN OTP & TẠO TÀI KHOẢN
    @PostMapping("/register/verify")
    public ResponseEntity<?> verifyAndRegister(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không được để trống"));
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu phải có ít nhất 6 ký tự"));
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email này đã tồn tại"));
        }

        String otpError = verifyOtp(email, "REGISTER", request.getOtp());
        if (otpError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", otpError));
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("USER");
        newUser.setFullName(
                (request.getFullName() != null && !request.getFullName().isBlank())
                        ? request.getFullName().trim()
                        : email.split("@")[0]
        );

        User savedUser = userRepository.save(newUser);
        return ResponseEntity.ok(buildAuthResponse(savedUser, "Đăng ký và xác thực email thành công"));
    }

    // 4. QUÊN MẬT KHẨU BƯỚC 1: GỬI MÃ OTP
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> req) {
        String rawEmail = req.get("email");
        if (rawEmail == null || rawEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập email đã đăng ký"));
        }

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy tài khoản nào với email này"));
        }

        try {
            String otp = createAndSaveOtp(email, "RESET_PASSWORD");
            boolean sent = emailService.sendOtpEmail(email, otp, "RESET_PASSWORD");
            String msg = sent
                    ? "Đã gửi mã đặt lại mật khẩu tới email " + email + " (hiệu lực 10 phút)."
                    : "Chưa cấu hình SMTP/Brevo — vui lòng xem mã OTP trên cửa sổ Console Backend để test.";
            return ResponseEntity.ok(Map.of("message", msg, "emailSent", sent));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không gửi được email: " + e.getMessage()));
        }
    }

    // 5. QUÊN MẬT KHẨU BƯỚC 2: XÁC NHẬN OTP & ĐẶT MẬT KHẨU MỚI
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không được để trống"));
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới phải có ít nhất 6 ký tự"));
        }

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy tài khoản"));
        }

        String otpError = verifyOtp(email, "RESET_PASSWORD", req.getOtp());
        if (otpError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", otpError));
        }

        User user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới."));
    }

    // 6. ĐỔI MẬT KHẨU (KHI ĐÃ ĐĂNG NHẬP)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        User currentUser = currentUserService.getCurrentUser();

        if (req.getCurrentPassword() == null || req.getCurrentPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập mật khẩu hiện tại"));
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới phải có ít nhất 6 ký tự"));
        }
        if (!matchesPassword(req.getCurrentPassword(), currentUser.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu hiện tại không chính xác"));
        }
        if (req.getCurrentPassword().equals(req.getNewPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới phải khác mật khẩu hiện tại"));
        }

        currentUser.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(currentUser);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    }

    // 7. ĐĂNG NHẬP BẰNG GOOGLE (XÁC THỰC GOOGLE ID TOKEN)
    @PostMapping("/google")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest req) {
        if (req.getCredential() == null || req.getCredential().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Thiếu mã xác thực từ Google"));
        }

        try {
            Map<String, Object> tokenInfo = RestClient.create()
                    .get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", req.getCredential().trim())
                    .retrieve()
                    .body(Map.class);

            if (tokenInfo == null || tokenInfo.get("email") == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Token Google không hợp lệ"));
            }

            if (googleClientId != null && !googleClientId.isBlank()) {
                String aud = String.valueOf(tokenInfo.get("aud"));
                if (!googleClientId.trim().equals(aud)) {
                    return ResponseEntity.status(401).body(Map.of("message", "Google Client ID không khớp"));
                }
            }

            String emailVerified = String.valueOf(tokenInfo.get("email_verified"));
            if (!"true".equalsIgnoreCase(emailVerified)) {
                return ResponseEntity.status(401).body(Map.of("message", "Email Google chưa được xác minh"));
            }

            String email = String.valueOf(tokenInfo.get("email")).trim().toLowerCase(Locale.ROOT);
            String name = tokenInfo.get("name") != null ? String.valueOf(tokenInfo.get("name")).trim() : email.split("@")[0];

            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User created = new User();
                created.setEmail(email);
                created.setFullName(name);
                created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                created.setRole("USER");
                return userRepository.save(created);
            });

            return ResponseEntity.ok(buildAuthResponse(user, "Đăng nhập Google thành công"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Xác thực Google thất bại hoặc token đã hết hạn"));
        }
    }

    // 8. LẤY THÔNG TIN HỒ SƠ CÁ NHÂN
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        User currentUser = currentUserService.getCurrentUser();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", currentUser.getId());
        body.put("email", currentUser.getEmail());
        body.put("fullName", currentUser.getFullName() != null ? currentUser.getFullName() : "Người dùng");
        body.put("role", currentUser.getRole() != null ? currentUser.getRole() : "USER");
        body.put("monthlyBudgetLimit", currentUser.getMonthlyBudgetLimit() != null ? currentUser.getMonthlyBudgetLimit() : 0.0);
        return ResponseEntity.ok(body);
    }

    // 9. CẬP NHẬT HỒ SƠ CÁ NHÂN & NGÂN SÁCH TỔNG THÁNG
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> req) {
        User currentUser = currentUserService.getCurrentUser();
        if (req.containsKey("fullName") && req.get("fullName") != null) {
            String name = String.valueOf(req.get("fullName")).trim();
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Họ tên không được để trống"));
            }
            currentUser.setFullName(name);
        }
        if (req.containsKey("monthlyBudgetLimit") && req.get("monthlyBudgetLimit") != null) {
            try {
                double limit = Double.parseDouble(String.valueOf(req.get("monthlyBudgetLimit")));
                currentUser.setMonthlyBudgetLimit(Math.max(0.0, limit));
            } catch (NumberFormatException ignored) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ngân sách tháng không hợp lệ"));
            }
        }
        User saved = userRepository.save(currentUser);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Cập nhật hồ sơ thành công!");
        body.put("id", saved.getId());
        body.put("email", saved.getEmail());
        body.put("fullName", saved.getFullName());
        body.put("role", saved.getRole() != null ? saved.getRole() : "USER");
        body.put("monthlyBudgetLimit", saved.getMonthlyBudgetLimit() != null ? saved.getMonthlyBudgetLimit() : 0.0);
        return ResponseEntity.ok(body);
    }
}