package com.cyberflow.admin.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统用户管理控制器。
 * <p>
 * 提供用户的增删改查及角色分配功能，所有接口均受权限保护。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/system/user")
@RequiredArgsConstructor
public class SysUserController {

    /** 用户业务服务 */
    private final SysUserService userService;

    /**
     * 分页查询用户列表。
     * <p>
     * 按创建时间倒序排序，返回的用户数据已清除密码字段以保障安全。
     * </p>
     *
     * @param page 当前页码，默认为 1
     * @param size 每页条数，默认为 10
     * @return 分页用户数据，密码字段已脱敏
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<Page<SysUser>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        Page<SysUser> result = userService.lambdaQuery()
                .orderByDesc(SysUser::getCreatedAt)
                .page(new Page<>(page, size));
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(result);
    }

    /**
     * 创建新用户。
     * <p>
     * 密码在前端以明文传入，后端使用 BCrypt 加密后存储。
     * </p>
     *
     * @param user 用户实体（包含 username、password、nickname 等字段）
     * @return 操作成功的空响应
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public Result<Void> create(@RequestBody SysUser user) {
        userService.createUser(user);
        return Result.ok();
    }

    /**
     * 更新用户信息。
     * <p>
     * 若传入的密码非空则更新密码，否则保留原密码不变。
     * </p>
     *
     * @param id   待更新的用户 ID
     * @param user 更新后的用户实体
     * @return 操作成功的空响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        userService.updateUser(user);
        return Result.ok();
    }

    /**
     * 删除用户。
     *
     * @param id 待删除的用户 ID
     * @return 操作成功的空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return Result.ok();
    }

    /**
     * 为用户分配角色。
     * <p>
     * 操作过程为：先删除用户原有角色关联，再批量插入新的角色关联。
     * </p>
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表
     * @return 操作成功的空响应
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return Result.ok();
    }
}
