package com.cyberflow.admin.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装类。
 * <p>
 * 所有 Controller 层的接口返回值均应使用此类进行包装，
 * 确保前端能获得格式一致的 JSON 响应体，包含状态码、消息和数据体。
 * </p>
 *
 * @param <T> 响应数据的类型
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码，200 表示成功，其他值表示异常 */
    private int code;

    /** 响应消息，成功时通常为 "success"，失败时为具体错误描述 */
    private String msg;

    /** 响应数据体，可为空 */
    private T data;

    /**
     * 构造一个成功响应，并附带数据体。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 状态码为 200 的成功 Result
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 构造一个不带数据体的成功响应。
     *
     * @param <T> 数据类型
     * @return 状态码为 200、data 为 null 的成功 Result
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造一个带自定义状态码和消息的失败响应。
     *
     * @param code 业务错误码
     * @param msg  错误描述
     * @param <T>  数据类型
     * @return 失败 Result
     */
    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 构造一个默认 500 错误的失败响应。
     *
     * @param msg 错误描述
     * @param <T> 数据类型
     * @return 状态码为 500 的失败 Result
     */
    public static <T> Result<T> fail(String msg) {
        return fail(500, msg);
    }
}
