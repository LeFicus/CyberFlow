package com.cyberflow.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，提供基础 CRUD 操作。
 * 定义了自定义 SQL 方法用于查询用户的角色编码和权限标识。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户 ID 查询其关联的角色编码列表。
     * <p>
     * 通过三表联查（sys_user_role → sys_role），仅返回状态为启用的角色。
     * </p>
     *
     * @param userId 用户唯一标识
     * @return 角色编码列表，如 ["ROLE_ADMIN", "ROLE_USER"]
     */
    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(Long userId);

    /**
     * 根据用户 ID 查询其所有权限标识。
     * <p>
     * 通过四表联查（sys_user_role → sys_role_menu → sys_menu），
     * 返回去重后的权限标识，仅包含状态启用且有权限标识的菜单项。
     * </p>
     *
     * @param userId 用户唯一标识
     * @return 权限标识列表，如 ["system:user:list", "system:user:create"]
     */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.perms IS NOT NULL")
    List<String> selectPermissionsByUserId(Long userId);
}
