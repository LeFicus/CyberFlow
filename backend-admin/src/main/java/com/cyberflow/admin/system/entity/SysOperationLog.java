package com.cyberflow.admin.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志实体类，对应数据库表 {@code sys_operation_log}。
 * <p>
 * 记录用户在后管系统中的关键操作（增、删、改），包括操作类型、所属模块、
 * 请求 URL、IP 地址、执行耗时、操作结果及错误信息等。由 {@code OperationLogAspect}
 * 切面自动采集和保存。
 * </p>
 *
 * @author CyberFlow Team
 * @see com.cyberflow.admin.common.OperationLogAspect
 * @since 1.0.0
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    /** 日志唯一标识，数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作类型：CREATE / UPDATE / DELETE */
    private String operation;

    /** 所属模块：SYSTEM / CRAWLER / DASHBOARD */
    private String module;

    /** 操作目标描述 */
    private String target;

    /** HTTP 请求方法：GET / POST / PUT / DELETE */
    private String requestMethod;

    /** 请求的 URL 路径 */
    private String requestUrl;

    /** 请求参数（截取前 1000 字符） */
    private String requestParams;

    /** 客户端 IP 地址 */
    private String ip;

    /** 操作状态：1-成功，0-失败 */
    private Integer status;

    /** 失败时的异常错误信息 */
    private String errorMsg;

    /** 请求执行耗时（毫秒） */
    private Long costTime;

    /** 日志记录创建时间 */
    private LocalDateTime createdAt;
}
