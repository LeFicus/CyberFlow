package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体类，对应数据库表 {@code sys_user_role}。
 * <p>
 * 用于建立用户与角色之间的多对多关联关系。
 * 一个用户可以拥有多个角色，一个角色也可以被分配给多个用户。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /** 关联记录唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，外键关联 {@code sys_user.id} */
    private Long userId;

    /** 角色 ID，外键关联 {@code sys_role.id} */
    private Long roleId;
}
