package com.cyberflow.admin.dashboard.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.dashboard.mapper.OrderFraudReviewMapper;
import com.cyberflow.admin.dashboard.service.OrderFraudReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard/orders/{group}/{id}/fraud-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and hasAuthority('dashboard:order:view')")
public class OrderFraudReviewController {
    private final OrderFraudReviewService service;
    private final OrderFraudReviewMapper mapper;

    public record ReviewRequest(String email, String decision, String note) {}

    @PostMapping
    public Result<Void> review(@PathVariable String group, @PathVariable long id,
                               @RequestBody ReviewRequest request, Authentication authentication) {
        service.review(group, id, request.email(), request.decision(), request.note(), authentication.getName());
        return Result.ok();
    }

    @GetMapping
    public Result<List<Map<String, Object>>> history(@PathVariable String group, @PathVariable long id) {
        return Result.ok(mapper.history(group, id));
    }
}
