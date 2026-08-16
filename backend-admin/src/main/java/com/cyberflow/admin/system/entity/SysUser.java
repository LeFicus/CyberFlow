package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体类，对应数据库表 {@code sys_user}。
 * <p>
 * 存储后台管理系统的用户信息，包括登录凭证、个人信息和账户状态。
 * 密码字段在序列化返回给前端时应注意脱敏处理。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 用户唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名，唯一标识，用于认证 */
    private String username;

    /** 加密后的密码，使用 BCrypt 算法哈希存储 */
    private String password;

    /** 用户昵称或显示名称 */
    private String nickname;

    /** 外部站点/订单数据中的管理员名称，用于普通用户的数据范围控制 */
    private String dataOwner;

    /** 用户邮箱地址 */
    private String email;

    /** 账户状态：1-启用，0-禁用 */
    private Integer status;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}
