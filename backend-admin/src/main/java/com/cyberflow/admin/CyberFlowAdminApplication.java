package com.cyberflow.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * CyberFlow 后台管理系统启动类。
 * <p>
 * 应用程序的主入口，负责引导 Spring Boot 容器的启动、自动配置扫描以及重试机制的启用。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableRetry
public class CyberFlowAdminApplication {

    /**
     * 应用程序主入口方法。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CyberFlowAdminApplication.class, args);
    }
}
