package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体类，对应数据库表 {@code sys_role}。
 * <p>
 * 基于 RBAC（基于角色的访问控制）模型，角色是权限分配的基本单位。
 * 每个角色拥有一组菜单权限，用户通过关联角色获得相应的操作权限。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 角色唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色显示名称，如"系统管理员"、"普通用户" */
    private String roleName;

    /** 角色编码，如 "ROLE_ADMIN"、"ROLE_USER"，用于权限判断和代码引用 */
    private String roleCode;

    /** 角色描述信息 */
    private String description;

    /** 角色状态：1-启用，0-禁用 */
    private Integer status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}
