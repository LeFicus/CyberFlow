package com.cyberflow.admin.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyberflow.admin.system.entity.SysRole;
import com.cyberflow.admin.system.entity.SysRoleMenu;
import com.cyberflow.admin.system.mapper.SysRoleMapper;
import com.cyberflow.admin.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统角色业务服务。
 * <p>
 * 继承 MyBatis-Plus 的 {@link ServiceImpl}，提供角色的 CRUD 操作。
 * 支持角色与菜单权限的关联管理。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    /** 角色-菜单关联 Mapper，用于菜单权限分配操作 */
    private final SysRoleMenuMapper roleMenuMapper;

    /**
     * 为角色分配菜单权限。
     * <p>
     * 先删除角色原有的所有菜单权限关联，再批量插入新的关联。
     * 整个过程在同一个事务中执行。
     * </p>
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表，可为空或 null（清空所有权限）
     */
    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null) {
            menuIds.forEach(menuId -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            });
        }
    }
}
