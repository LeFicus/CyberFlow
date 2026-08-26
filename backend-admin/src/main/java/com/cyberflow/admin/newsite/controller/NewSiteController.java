package com.cyberflow.admin.newsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.model.NewSiteBatchCreateRequest;
import com.cyberflow.admin.newsite.model.NewSiteStatusRequest;
import com.cyberflow.admin.newsite.service.NewSiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/new-site")
@RequiredArgsConstructor
public class NewSiteController {

    private final NewSiteService newSiteService;

    @GetMapping
    @PreAuthorize("hasAuthority('newsite:list')")
    public Result<Page<NewSite>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword) {
        return Result.ok(newSiteService.page(page, size, status, keyword));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('newsite:list')")
    public Result<Map<String, Object>> options() {
        return Result.ok(newSiteService.options());
    }

    @GetMapping("/ai-config")
    @PreAuthorize("hasAnyAuthority('newsite:list', 'newsite:config')")
    public Result<Map<String, Object>> aiConfig() {
        return Result.ok(newSiteService.aiConfig());
    }

    @PutMapping("/ai-config")
    @PreAuthorize("hasAuthority('newsite:config')")
    public Result<Map<String, Object>> updateAiConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(newSiteService.updateAiConfig(body));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('newsite:create')")
    public Result<?> createBatch(@Valid @RequestBody NewSiteBatchCreateRequest request) {
        return Result.ok(newSiteService.createBatch(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('newsite:status')")
    public Result<NewSite> updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody NewSiteStatusRequest request) {
        return Result.ok(newSiteService.updateStatus(id, request.getStatus()));
    }
}
