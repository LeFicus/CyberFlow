package com.cyberflow.admin.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysOperationLog;
import com.cyberflow.admin.system.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志查询控制器。
 * <p>
 * 提供操作日志的分页查询功能，支持按用户名和模块筛选。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/system/log")
@RequiredArgsConstructor
public class SysOperationLogController {

    /** 操作日志业务服务 */
    private final SysOperationLogService logService;

    /**
     * 分页查询操作日志。
     * <p>
     * 按创建时间倒序排列，支持按用户名和模块名进行可选过滤。
     * 当过滤参数为 null 或空白时，该条件不生效。
     * </p>
     *
     * @param page     当前页码，默认为 1
     * @param size     每页条数，默认为 10
     * @param username 按用户名筛选（可选）
     * @param module   按模块名筛选（可选），如 SYSTEM、CRAWLER、DASHBOARD
     * @return 分页操作日志数据
     */
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
