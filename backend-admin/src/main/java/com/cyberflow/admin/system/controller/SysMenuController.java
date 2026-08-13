package com.cyberflow.admin.system.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysMenu;
import com.cyberflow.admin.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统菜单/权限管理控制器。
 * <p>
 * 提供菜单的树形查询及增删改功能。菜单支持三级结构：
 * 目录（menuType=0）→ 菜单（menuType=1）→ 按钮（menuType=2）。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    /** 菜单业务服务 */
    private final SysMenuService menuService;

    /**
     * 获取完整的菜单树结构。
     * <p>
     * 将所有菜单数据按 parentId 层级关系构建为树形结构，
     * 同级菜单按 sortOrder 升序排列。
     * </p>
     *
     * @return 菜单树列表，顶级节点 parentId 为 0
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<SysMenu>> tree() {
        return Result.ok(menuService.getMenuTree());
    }

    /**
     * 创建新菜单/权限。
     *
     * @param menu 菜单实体（包含 parentId、menuName、menuType 等字段）
     * @return 操作成功的空响应
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    public Result<Void> create(@RequestBody SysMenu menu) {
        menuService.save(menu);
        return Result.ok();
    }

    /**
     * 更新菜单/权限信息。
     *
     * @param id   待更新的菜单 ID
     * @param menu 更新后的菜单实体
     * @return 操作成功的空响应
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        menuService.updateById(menu);
        return Result.ok();
    }

    /**
     * 删除菜单/权限。
     *
     * @param id 待删除的菜单 ID
     * @return 操作成功的空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.ok();
    }
}
