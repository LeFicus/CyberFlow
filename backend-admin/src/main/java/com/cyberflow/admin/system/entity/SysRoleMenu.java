package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联实体类，对应数据库表 {@code sys_role_menu}。
 * <p>
 * 用于建立角色与菜单（权限）之间的多对多关联关系。
 * 每个角色可以拥有多个菜单权限，每个菜单也可以被分配给多个角色。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    /** 关联记录唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID，外键关联 {@code sys_role.id} */
    private Long roleId;

    /** 菜单 ID，外键关联 {@code sys_menu.id} */
    private Long menuId;
}
