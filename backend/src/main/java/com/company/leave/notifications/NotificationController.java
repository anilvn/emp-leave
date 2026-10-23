package com.company.leave.notifications;

import java.util.List;

import com.company.leave.security.AccessPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "The authenticated user's notification history")
public class NotificationController {

    private final NotificationService notificationService;
    private final AccessPolicy accessPolicy;

    NotificationController(NotificationService notificationService, AccessPolicy accessPolicy) {
        this.notificationService = notificationService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(summary = "Get my notification history")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caller-owned notification rows"),
        @ApiResponse(responseCode = "401", description = "No valid access_token cookie"),
        @ApiResponse(responseCode = "403", description = "Password change is required")
    })
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotificationHistory() {
        long userId = accessPolicy.currentUserId();
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }
}
