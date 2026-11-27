package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.Category;
import personalfinance.entity.User;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.TransactionRepository;
import personalfinance.repository.UserRepository;
import personalfinance.repository.WalletRepository;
import personalfinance.security.CurrentUserService;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    private boolean isAdmin() {
        User current = currentUserService.getCurrentUser();
        return current != null && "ADMIN".equalsIgnoreCase(current.getRole());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập"));
        }
        Runtime rt = Runtime.getRuntime();
        long usedMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMemMb = rt.maxMemory() / (1024 * 1024);
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        List<Category> defaultCategories = categoryRepository.findAll().stream()
                .filter(c -> c.getUser() == null)
                .toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("status", "UP");
        stats.put("totalUsers", userRepository.count());
        stats.put("totalWallets", walletRepository.count());
        stats.put("totalTransactionsCount", transactionRepository.count());
        stats.put("defaultCategoriesCount", defaultCategories.size());
        stats.put("memoryUsedMb", usedMemMb);
        stats.put("memoryMaxMb", maxMemMb);
        stats.put("uptimeSeconds", uptimeSeconds);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/default-categories")
    public ResponseEntity<?> listDefaultCategories() {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập"));
        }
        List<Category> defaults = categoryRepository.findAll().stream()
                .filter(c -> c.getUser() == null)
                .toList();
        return ResponseEntity.ok(defaults);
    }

    @Data
    public static class DefaultCategoryRequest {
        private String name;
        private String type;
        private String color;
        private String icon;
    }

    @PostMapping("/default-categories")
    public ResponseEntity<?> createDefaultCategory(@RequestBody DefaultCategoryRequest req) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập"));
        }
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên danh mục mặc định không được trống"));
        }
        Category cat = Category.builder()
                .name(req.getName().trim())
                .type(req.getType() != null ? req.getType() : "EXPENSE")
                .color(req.getColor() != null ? req.getColor() : "#6366f1")
                .icon(req.getIcon() != null ? req.getIcon() : "tag")
                .budgetLimit(0.0)
                .user(null)
                .build();
        return ResponseEntity.ok(categoryRepository.save(cat));
    }

    @PutMapping("/default-categories/{id}")
    public ResponseEntity<?> updateDefaultCategory(@PathVariable Long id, @RequestBody DefaultCategoryRequest req) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập"));
        }
        Category cat = categoryRepository.findById(id).orElse(null);
        if (cat == null || cat.getUser() != null) {
            return ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy danh mục mặc định"));
        }
        if (req.getName() != null && !req.getName().isBlank()) cat.setName(req.getName().trim());
        if (req.getType() != null) cat.setType(req.getType());
        if (req.getColor() != null) cat.setColor(req.getColor());
        if (req.getIcon() != null) cat.setIcon(req.getIcon());
        return ResponseEntity.ok(categoryRepository.save(cat));
    }

    @DeleteMapping("/default-categories/{id}")
    public ResponseEntity<?> deleteDefaultCategory(@PathVariable Long id) {
        if (!isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("message", "Chỉ Quản trị viên (ADMIN) mới có quyền truy cập"));
        }
        Category cat = categoryRepository.findById(id).orElse(null);
        if (cat == null || cat.getUser() != null) {
            return ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy danh mục mặc định"));
        }
        categoryRepository.delete(cat);
        return ResponseEntity.ok(Map.of("message", "Đã xóa danh mục mặc định"));
    }
}
