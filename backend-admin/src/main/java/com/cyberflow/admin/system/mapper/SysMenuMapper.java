package com.cyberflow.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统菜单 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，提供菜单的基础 CRUD 操作。
 * 定义了自定义 SQL 用于按用户查询其有权限访问的菜单列表。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 根据用户 ID 查询其有权限访问的菜单列表。
     * <p>
     * 通过四表联查（sys_user_role → sys_role_menu → sys_menu），
     * 返回状态启用、类型为目录或菜单的项，按 sort_order 排序。
     * </p>
     *
     * @param userId 用户唯一标识
     * @return 用户可见的菜单列表
     */
    @Select("SELECT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.menu_type IN (0, 1) " +
            "ORDER BY m.sort_order")
    List<SysMenu> selectMenusByUserId(Long userId);
}
