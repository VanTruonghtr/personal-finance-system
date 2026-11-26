package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.Category;
import personalfinance.entity.User;
import personalfinance.repository.CategoryRepository;
import personalfinance.security.CurrentUserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(categoryRepository.findByUserIsNullOrUserId(userId));
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
            User owner = currentUserService.getCurrentUser();
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
            Long userId = currentUserService.getCurrentUserId();
            Category cat = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
            if (cat.getUser() != null && !cat.getUser().getId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("message", "Không có quyền sửa danh mục này"));
            }
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
            Long userId = currentUserService.getCurrentUserId();
            Category cat = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
            if (cat.getUser() == null || !cat.getUser().getId().equals(userId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Không thể xóa danh mục mặc định hệ thống"));
            }
            categoryRepository.delete(cat);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể xóa danh mục này"));
        }
    }
}