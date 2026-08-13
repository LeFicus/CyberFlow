package com.cyberflow.admin.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 跨域资源共享（CORS）配置类。
 * <p>
 * 允许前端应用（可能部署在不同域名或端口下）以跨域方式访问后端 API，
 * 支持携带凭证（Cookie/Authorization 头）的跨域请求。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Configuration
public class CorsConfig {

    /**
     * 创建并注册全局 CORS 过滤器。
     * <p>
     * 配置了允许的来源模式、HTTP 方法、请求头、凭证携带及预检请求缓存时间。
     * </p>
     *
     * @return 全局 CorsFilter 实例，作用于所有路径（/**）
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有来源的跨域请求（使用模式匹配，与 allowCredentials=true 配合使用）
        config.setAllowedOriginPatterns(List.of("*"));
        // 允许的 HTTP 请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许所有请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许携带认证凭证（如 Cookie、Authorization 头）
        config.setAllowCredentials(true);
        // 预检请求的缓存时间（秒），减少预检请求次数
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 将 CORS 配置应用到所有路径
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
