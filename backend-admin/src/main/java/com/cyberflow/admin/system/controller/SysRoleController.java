package com.cyberflow.admin.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysRole;
import com.cyberflow.admin.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统角色管理控制器。
 * <p>
 * 提供角色的增删改查及菜单权限分配功能。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    /** 角色业务服务 */
    private final SysRoleService roleService;

    /**
     * 分页查询角色列表。
     *
     * @param page 当前页码，默认为 1
     * @param size 每页条数，默认为 10
     * @return 分页角色数据
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<Page<SysRole>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(roleService.page(new Page<>(page, size)));
    }

    /**
     * 查询全部角色（不分页）。
     * <p>
     * 常用于下拉选择框的数据源。
     * </p>
     *
     * @return 全部角色列表
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<List<SysRole>> all() {
        return Result.ok(roleService.list());
    }

    /**
     * 创建新角色。
     *
     * @param role 角色实体（包含 roleName、roleCode 等字段）
     * @return 操作成功的空响应
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public Result<Void> create(@RequestBody SysRole role) {
        roleService.save(role);
        return Result.ok();
    }

    /**
     * 更新角色信息。
     *
     * @param id   待更新的角色 ID
     * @param role 更新后的角色实体
     * @return 操作成功的空响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);
        roleService.updateById(role);
        return Result.ok();
    }

    /**
     * 删除角色。
     *
     * @param id 待删除的角色 ID
     * @return 操作成功的空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.ok();
    }

    /**
     * 为角色分配菜单权限。
     * <p>
     * 操作过程为：先删除角色原有菜单关联，再批量插入新的菜单关联。
     * </p>
     *
     * @param id      角色 ID
     * @param menuIds 菜单 ID 列表
     * @return 操作成功的空响应
     */
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:assign')")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return Result.ok();
    }

    /** 查询角色当前绑定的菜单/按钮权限 ID，用于分配弹窗回显。 */
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.ok(roleService.getBaseMapper().selectMenuIdsByRoleId(id));
    }
}
