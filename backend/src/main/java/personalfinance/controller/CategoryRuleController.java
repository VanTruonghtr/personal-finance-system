package personalfinance.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personalfinance.entity.Category;
import personalfinance.entity.CategoryRule;
import personalfinance.entity.User;
import personalfinance.repository.CategoryRepository;
import personalfinance.repository.CategoryRuleRepository;
import personalfinance.security.CurrentUserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class CategoryRuleController {

    private final CategoryRuleRepository categoryRuleRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<CategoryRule>> getMyRules() {
        Long userId = currentUserService.getCurrentUserId();
        return ResponseEntity.ok(categoryRuleRepository.findByUserIdOrderByPriorityDescIdDesc(userId));
    }

    @Data
    public static class RuleRequest {
        private String keyword;
        private Long categoryId;
        private Integer priority;
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody RuleRequest req) {
        if (req.getKeyword() == null || req.getKeyword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Từ khóa không được để trống"));
        }
        if (req.getCategoryId() == null || req.getCategoryId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn danh mục áp dụng"));
        }
        User owner = currentUserService.getCurrentUser();
        Category category = categoryRepository.findById(req.getCategoryId())
                .filter(c -> c.getUser() == null || c.getUser().getId().equals(owner.getId()))
                .orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy danh mục hợp lệ"));
        }

        CategoryRule rule = CategoryRule.builder()
                .keyword(req.getKeyword().trim())
                .category(category)
                .priority(req.getPriority() != null ? req.getPriority() : 0)
                .user(owner)
                .build();
        return ResponseEntity.ok(categoryRuleRepository.save(rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id) {
        Long userId = currentUserService.getCurrentUserId();
        CategoryRule rule = categoryRuleRepository.findByIdAndUserId(id, userId).orElse(null);
        if (rule == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy quy tắc từ khóa"));
        }
        categoryRuleRepository.delete(rule);
        return ResponseEntity.ok(Map.of("message", "Đã xóa quy tắc từ khóa"));
    }
}
