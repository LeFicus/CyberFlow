package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统菜单/权限实体类，对应数据库表 {@code sys_menu}。
 * <p>
 * 支持树形结构的菜单体系，包含菜单类型（目录/菜单/按钮）、路由信息、
 * 权限标识等字段。通过 {@code parentId} 字段实现父子级联关系，
 * {@code children} 字段用于前端构建菜单树（非数据库字段）。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    /** 菜单唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级菜单 ID，0 表示顶级菜单 */
    private Long parentId;

    /** 菜单名称，用于前端显示 */
    private String menuName;

    /** 菜单类型：0-目录，1-菜单，2-按钮 */
    private Integer menuType;

    /** 权限标识字符串，如 "system:user:list"，用于 Spring Security 权限校验 */
    private String perms;

    /** 前端路由路径，如 "/system/user" */
    private String path;

    /** 前端组件路径，如 "system/user/index" */
    private String component;

    /** 菜单图标（前端图标库的标识） */
    private String icon;

    /** 排序字段，数值越小越靠前 */
    private Integer sortOrder;

    /** 菜单状态：1-启用，0-禁用 */
    private Integer status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;

    /** 子菜单列表，非数据库字段，用于构建菜单树 */
    @TableField(exist = false)
    private List<SysMenu> children;
}
