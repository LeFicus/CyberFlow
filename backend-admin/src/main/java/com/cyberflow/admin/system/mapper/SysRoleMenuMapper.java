package com.cyberflow.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色-菜单关联 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，提供角色菜单关联的基础 CRUD 操作。
 * 定义了批量删除角色所有菜单权限关联的方法。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 删除指定角色的所有菜单权限关联。
     * <p>
     * 在为角色重新分配菜单权限前调用，清除旧的关联记录。
     * </p>
     *
     * @param roleId 角色唯一标识
     * @return 被删除的记录数
     */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
