package personalfinance.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping(value = "/swagger-ui.html", produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerUi() {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>HTR FinMind — Swagger API Documentation</title>
                  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
                  <style>
                    body { margin: 0; background: #f5f5f7; font-family: -apple-system, BlinkMacSystemFont, sans-serif; }
                    .topbar { display: none; }
                  </style>
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                  <script>
                    window.onload = () => {
                      SwaggerUIBundle({
                        url: '/v3/api-docs',
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        persistAuthorization: true
                      });
                    };
                  </script>
                </body>
                </html>
                """;
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openApiSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "HTR FinMind Personal Finance API",
                "version", "1.0.0",
                "description", "RESTful API hệ thống quản lý tài chính cá nhân tích hợp Quick-Parse Input, đối soát sao kê CSV, Burn-rate Alert, Safe-to-Spend/day, phân tích biến động kỳ và trợ lý AI."
        ));
        spec.put("components", Map.of(
                "securitySchemes", Map.of(
                        "bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT")
                )
        ));
        spec.put("security", List.of(Map.of("bearerAuth", List.of())));

        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/api/auth/login", op("post", "Auth", "Đăng nhập bằng Email & Mật khẩu", false));
        paths.put("/api/auth/register/send-otp", op("post", "Auth", "Gửi mã OTP đăng ký tài khoản", false));
        paths.put("/api/auth/register/verify", op("post", "Auth", "Xác thực OTP và hoàn tất đăng ký", false));
        paths.put("/api/auth/google", op("post", "Auth", "Đăng nhập bằng Google OAuth2", false));
        paths.put("/api/auth/me", op("get", "Auth", "Lấy thông tin hồ sơ người dùng hiện tại", true));
        paths.put("/api/auth/profile", op("put", "Auth", "Cập nhật hồ sơ & ngân sách tổng tháng", true));
        paths.put("/api/auth/change-password", op("post", "Auth", "Đổi mật khẩu tài khoản", true));

        paths.put("/api/wallets", multiOp(Map.of("get", "Lấy danh sách ví của người dùng", "post", "Tạo ví mới"), "Wallets"));
        paths.put("/api/wallets/{id}", multiOp(Map.of("put", "Cập nhật ví", "delete", "Xóa ví"), "Wallets"));

        paths.put("/api/categories", multiOp(Map.of("get", "Lấy danh sách danh mục & hạn mức", "post", "Tạo danh mục mới"), "Categories"));
        paths.put("/api/rules", multiOp(Map.of("get", "Lấy bộ quy tắc từ khóa tự động phân loại", "post", "Thêm quy tắc từ khóa"), "Auto-Categorization Rules"));
        paths.put("/api/rules/{id}", op("delete", "Auto-Categorization Rules", "Xóa quy tắc từ khóa", true));

        paths.put("/api/transactions", multiOp(Map.of("get", "Danh sách giao dịch", "post", "Tạo giao dịch thu/chi/chuyển ví"), "Transactions"));
        paths.put("/api/transactions/quick-parse/preview", op("post", "Quick-Parse", "Xem trước kết quả phân tích chuỗi nhập nhanh 1 dòng", true));
        paths.put("/api/transactions/quick-parse", op("post", "Quick-Parse", "Phân tích chuỗi 1 dòng và tạo giao dịch tức thì", true));
        paths.put("/api/transactions/import/preview", op("post", "Statement Import", "Đối soát tệp sao kê CSV/Excel, gán danh mục và phát hiện trùng lặp", true));
        paths.put("/api/transactions/import/commit", op("post", "Statement Import", "Lưu hàng loạt giao dịch hợp lệ đã đối soát", true));

        paths.put("/api/analytics/overview", op("get", "Analytics & Burn-Rate", "Tính Safe-to-Spend/day, Burn-rate Alert và phát hiện danh mục tăng đột biến", true));
        paths.put("/api/goals", multiOp(Map.of("get", "Danh sách mục tiêu tiết kiệm", "post", "Tạo mục tiêu mới"), "Savings Goals"));
        paths.put("/api/recurring", multiOp(Map.of("get", "Danh sách giao dịch định kỳ", "post", "Tạo khoản định kỳ"), "Recurring"));
        paths.put("/api/ai/analyze", op("get", "AI Advisor", "Phân tích sức khỏe tài chính & gợi ý", true));
        paths.put("/api/ai/ask", op("post", "AI Advisor", "Hỏi đáp trợ lý tài chính AI", true));

        paths.put("/api/admin/stats", op("get", "Admin", "Thống kê vận hành hệ thống (chỉ ADMIN, không xem giao dịch chi tiết)", true));
        paths.put("/api/admin/default-categories", multiOp(Map.of("get", "Danh mục mặc định hệ thống", "post", "Thêm danh mục mặc định"), "Admin"));

        spec.put("paths", paths);
        return spec;
    }

    private Map<String, Object> op(String method, String tag, String summary, boolean auth) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("tags", List.of(tag));
        detail.put("summary", summary);
        if (!auth) detail.put("security", List.of());
        detail.put("responses", Map.of("200", Map.of("description", "Thành công")));
        return Map.of(method, detail);
    }

    private Map<String, Object> multiOp(Map<String, String> methodSummaries, String tag) {
        Map<String, Object> node = new LinkedHashMap<>();
        methodSummaries.forEach((m, s) -> node.put(m, Map.of(
                "tags", List.of(tag),
                "summary", s,
                "responses", Map.of("200", Map.of("description", "Thành công"))
        )));
        return node;
    }
}
