package personalfinance.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:}")
    private String brevoSenderEmail;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    public boolean sendOtpEmail(String toEmail, String otpCode, String purpose) {
        boolean isRegister = "REGISTER".equalsIgnoreCase(purpose);
        String subject = isRegister
                ? "[HTR FinMind] Mã xác nhận đăng ký tài khoản: " + otpCode
                : "[HTR FinMind] Mã xác nhận đặt lại mật khẩu: " + otpCode;

        String actionTitle = isRegister ? "Xác nhận đăng ký tài khoản" : "Đặt lại mật khẩu";
        String actionDesc = isRegister
                ? "Cảm ơn bạn đã đăng ký sử dụng HTR FinMind. Vui lòng nhập mã xác nhận bên dưới để hoàn tất tạo tài khoản:"
                : "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản HTR FinMind. Vui lòng nhập mã xác nhận bên dưới:";

        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 16px;">
                  <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 16px;">
                    <div style="width: 36px; height: 36px; border-radius: 10px; background: #059669; color: #fff; font-weight: bold; font-size: 18px; line-height: 36px; text-align: center;">F</div>
                    <h2 style="margin: 0; color: #0f172a;">HTR FinMind</h2>
                  </div>
                  <h3 style="color: #0f172a; margin-bottom: 8px;">%s</h3>
                  <p style="color: #475569; font-size: 14px; line-height: 1.6;">%s</p>
                  <div style="margin: 24px 0; padding: 18px; background: #ecfdf5; border: 1px dashed #10b981; border-radius: 12px; text-align: center;">
                    <span style="font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #047857;">%s</span>
                  </div>
                  <p style="color: #64748b; font-size: 12px;">Mã có hiệu lực trong <strong>10 phút</strong>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</p>
                </div>
                """.formatted(actionTitle, actionDesc, otpCode);

        // 1. Uu tien Brevo HTTP API (khong bi chan port 587 tren Render Free)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            String sender = (brevoSenderEmail != null && !brevoSenderEmail.isBlank())
                    ? brevoSenderEmail.trim()
                    : (smtpUsername != null && !smtpUsername.isBlank() ? smtpUsername.trim() : "no-reply@htrfinmind.app");

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", "HTR FinMind", "email", sender),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", html
            );

            RestClient.create()
                    .post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", brevoApiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        }

        // 2. Gmail SMTP (tien dung khi chay local)
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null && smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(smtpUsername.trim(), "HTR FinMind");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
                return true;
            } catch (Exception e) {
                throw new RuntimeException("Gửi email thất bại: " + e.getMessage());
            }
        }

        // 3. Fallback khi chua cau hinh mail (in ra console de test local)
        System.out.println("=========================================================");
        System.out.println("[HTR FINMIND OTP - CHUA CAU HINH MAIL]");
        System.out.println("To: " + toEmail + " | Purpose: " + purpose + " | OTP: " + otpCode);
        System.out.println("=========================================================");
        return false;
    }
}