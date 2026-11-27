package personalfinance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import personalfinance.entity.User;
import personalfinance.security.CurrentUserService;
import personalfinance.service.AnalyticsService;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUserService currentUserService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsService.AnalyticsOverview> getOverview(
            @RequestParam(required = false) String month
    ) {
        User currentUser = currentUserService.getCurrentUser();
        return ResponseEntity.ok(analyticsService.getOverviewForUser(currentUser, month));
    }
}
