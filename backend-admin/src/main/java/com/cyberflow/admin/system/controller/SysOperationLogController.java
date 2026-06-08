package com.cyberflow.admin.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysOperationLog;
import com.cyberflow.admin.system.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/system/log")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogService logService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:log:view')")
    public Result<Page<SysOperationLog>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(required = false) String module) {
        var query = logService.lambdaQuery()
                .orderByDesc(SysOperationLog::getCreatedAt);
        if (username != null && !username.isBlank()) {
            query.eq(SysOperationLog::getUsername, username);
        }
        if (module != null && !module.isBlank()) {
            query.eq(SysOperationLog::getModule, module);
        }
        return Result.ok(query.page(new Page<>(page, size)));
    }
}
