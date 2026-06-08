package com.cyberflow.admin.common;

import com.cyberflow.admin.system.entity.SysOperationLog;
import com.cyberflow.admin.system.service.SysOperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogService logService;

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        var startTime = System.currentTimeMillis();
        var logEntry = new SysOperationLog();
        logEntry.setCreatedAt(LocalDateTime.now());

        try {
            // 获取请求信息
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                logEntry.setRequestMethod(request.getMethod());
                logEntry.setRequestUrl(request.getRequestURI());
                logEntry.setIp(request.getRemoteAddr());
                logEntry.setRequestParams(extractParams(request.getQueryString()));
            }

            // 获取用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                logEntry.setUsername(auth.getName());
            }

            // 推断操作类型
            String method = logEntry.getRequestMethod();
            String url = logEntry.getRequestUrl() != null ? logEntry.getRequestUrl() : "";
            logEntry.setOperation(inferOperation(method));
            logEntry.setModule(inferModule(url));

            Object result = joinPoint.proceed();

            logEntry.setStatus(1);
            logEntry.setCostTime(System.currentTimeMillis() - startTime);

            saveLog(logEntry);
            return result;
        } catch (Exception e) {
            logEntry.setStatus(0);
            logEntry.setErrorMsg(e.getMessage());
            logEntry.setCostTime(System.currentTimeMillis() - startTime);
            saveLog(logEntry);
            throw e;
        }
    }

    @Async
    void saveLog(SysOperationLog logEntry) {
        try {
            // 只记录 CUD 操作
            if (!"GET".equalsIgnoreCase(logEntry.getRequestMethod())) {
                logService.save(logEntry);
            }
        } catch (Exception e) {
            log.warn("Operation log save failed: {}", e.getMessage());
        }
    }

    private String inferOperation(String method) {
        return switch (method.toUpperCase()) {
            case "POST" -> "CREATE";
            case "PUT" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "QUERY";
        };
    }

    private String inferModule(String url) {
        if (url.contains("/system/")) return "SYSTEM";
        if (url.contains("/crawler/")) return "CRAWLER";
        if (url.contains("/dashboard/")) return "DASHBOARD";
        return "UNKNOWN";
    }

    private String extractParams(String queryString) {
        if (queryString == null || queryString.length() > 1000) {
            return queryString != null ? queryString.substring(0, 1000) : null;
        }
        return queryString;
    }
}
