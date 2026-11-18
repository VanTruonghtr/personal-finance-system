package vn.edu.uit.personalfinance.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.personalfinance.entity.Category;
import vn.edu.uit.personalfinance.entity.User;
import vn.edu.uit.personalfinance.repository.CategoryRepository;
import vn.edu.uit.personalfinance.repository.UserRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    @Transactional
    public ResponseEntity<List<Category>> getAllCategories() {
        try {
            // Đảm bảo cột name trong SQL Server là NVARCHAR để lưu tiếng Việt có dấu
            entityManager.createNativeQuery("ALTER TABLE categories ALTER COLUMN name NVARCHAR(255) NOT NULL").executeUpdate();
        } catch (Exception ignored) {}

        List<Category> list = categoryRepository.findAll();
        boolean updated = false;
        for (Category c : list) {
            String fixed = fixBrokenVietnamese(c.getName());
            if (!fixed.equals(c.getName())) {
                c.setName(fixed);
                updated = true;
            }
        }
        if (updated) {
            list = categoryRepository.saveAll(list);
        }
        return ResponseEntity.ok(list);
    }

    private String fixBrokenVietnamese(String name) {
        if (name == null) return "";
        return switch (name.trim()) {
            case "Ti?n luong", "Luong" -> "Tiền lương";
            case "Ti?n thu?ng" -> "Tiền thưởng";
            case "Thu nh?p khác" -> "Thu nhập khác";
            case "An u?ng" -> "Ăn uống";
            case "Di chuy?n" -> "Di chuyển";
            case "Nhà ? & Hóa don" -> "Nhà ở & Hóa đơn";
            case "Mua s?m" -> "Mua sắm";
            case "H?c t?p & S?c kh?e" -> "Học tập & Sức khỏe";
            case "Gi?i trí" -> "Giải trí";
            default -> name;
        };
    }

    @Data
    public static class CategoryRequest {
        private String name;
        private String type;
        private String color;
        private Double budgetLimit;
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody CategoryRequest req) {
        try {
            User owner = userRepository.findById(9L).orElse(null);
            Category cat = Category.builder()
                    .name(req.getName())
                    .type(req.getType() != null ? req.getType() : "EXPENSE")
                    .icon("tag")
                    .color(req.getColor() != null ? req.getColor() : "#6366f1")
                    .budgetLimit(req.getBudgetLimit() != null ? req.getBudgetLimit() : 0.0)
                    .user(owner)
                    .build();
            return ResponseEntity.ok(categoryRepository.save(cat));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi DB: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest req) {
        try {
            Category cat = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
            if (req.getName() != null) cat.setName(req.getName());
            if (req.getType() != null) cat.setType(req.getType());
            if (req.getColor() != null) cat.setColor(req.getColor());
            if (req.getBudgetLimit() != null) cat.setBudgetLimit(req.getBudgetLimit());
            return ResponseEntity.ok(categoryRepository.save(cat));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi cập nhật: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            categoryRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể xóa danh mục này"));
        }
    }
}