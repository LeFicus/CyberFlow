package com.cyberflow.admin.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 使用 Spring MVC 的 {@link RestControllerAdvice} 统一拦截 Controller 层抛出的异常，
 * 将其转换为标准的 {@link Result} 响应，避免异常信息直接暴露给前端。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理登录凭证错误异常（如用户名或密码错误）。
     *
     * @param e BadCredentialsException 异常实例
     * @return 401 状态码的失败响应
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.fail(401, "用户名或密码错误");
    }

    /**
     * 处理权限不足异常（如访问无权限的资源）。
     *
     * @param e AccessDeniedException 异常实例
     * @return 403 状态码的失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(403, "权限不足");
    }

    /**
     * 处理非法参数异常。
     *
     * @param e IllegalArgumentException 异常实例
     * @return 400 状态码的失败响应，携带异常消息
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }

    /**
     * 处理参数绑定/校验异常（如 {@code @Valid} 校验失败）。
     *
     * @param e BindException 异常实例
     * @return 400 状态码的失败响应，包含字段级别的校验错误描述
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    /**
     * 处理所有未被上述处理器捕获的异常（兜底处理）。
     * <p>
     * 记录完整异常堆栈日志，返回 500 内部服务器错误响应。
     * </p>
     *
     * @param e Exception 异常实例
     * @return 500 状态码的失败响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail("服务器内部错误: " + e.getMessage());
    }
}
