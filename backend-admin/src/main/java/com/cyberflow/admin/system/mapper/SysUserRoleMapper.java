package com.cyberflow.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户-角色关联 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，提供用户角色关联的基础 CRUD 操作。
 * 定义了批量删除用户所有角色关联的方法。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 删除指定用户的所有角色关联。
     * <p>
     * 在为用户重新分配角色前调用，清除旧的关联记录。
     * </p>
     *
     * @param userId 用户唯一标识
     * @return 被删除的记录数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
