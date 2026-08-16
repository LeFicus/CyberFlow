package com.cyberflow.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，提供角色的基础 CRUD 操作。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询角色当前绑定的菜单/按钮权限 ID。 */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId} ORDER BY menu_id")
    List<Long> selectMenuIdsByRoleId(Long roleId);
}
