package com.cyberflow.admin.system.config;

import com.cyberflow.admin.common.JwtUtils;
import com.cyberflow.admin.system.service.SysUserService;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security 安全配置类。
 * <p>
 * 配置了基于 JWT 的无状态认证体系。核心功能包括：
 * <ul>
 *   <li>密码加密器（BCrypt）</li>
 *   <li>会话策略（无状态 STATELESS）</li>
 *   <li>URL 访问权限控制</li>
 *   <li>JWT 认证过滤器，从请求头 {@code Authorization: Bearer <token>} 提取并解析令牌</li>
 * </ul>
 * 同时启用了方法级安全注解（{@link EnableMethodSecurity}），支持 {@code @PreAuthorize} 等注解。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT 工具类，用于解析和验证令牌 */
    private final JwtUtils jwtUtils;

    /** 从数据库读取最新角色和权限，避免角色调整后必须重新登录。 */
    private final SysUserMapper userMapper;

    /**
     * 注册密码编码器 Bean。
     * <p>
     * 使用 BCrypt 算法进行密码哈希，确保密码不以明文存储。
     * </p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置安全过滤链。
     * <p>
     * 定义了以下安全策略：
     * <ul>
     *   <li>关闭 CSRF（跨站请求伪造）防护（前后端分离架构不需要）</li>
     *   <li>启用 CORS（跨域资源共享）</li>
     *   <li>会话管理设为无状态（STATELESS），不创建 HttpSession</li>
     *   <li>登录和 Swagger 文档接口放行，OPTIONS 预检请求放行，其余请求需认证</li>
     *   <li>在 UsernamePasswordAuthenticationFilter 之前插入 JWT 认证过滤器</li>
     * </ul>
     * </p>
     *
     * @param http HttpSecurity 安全配置构建器
     * @return 构建完成的 SecurityFilterChain
     * @throws Exception 构建过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/auth/login").permitAll()
                .requestMatchers("/admin/swagger-ui/**", "/admin/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 认证过滤器 Bean。
     * <p>
     * 每次请求只执行一次的过滤器，从 HTTP 请求头 {@code Authorization} 中提取 Bearer Token，
     * 解析并验证令牌，将用户信息（用户名和权限列表）设置到 Spring Security 上下文中。
     * Token 无效时不阻断请求，仅保持匿名访问状态。
     * </p>
     *
     * @return OncePerRequestFilter 实例
     */
    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            /**
             * 对每个请求执行 JWT 认证逻辑。
             *
             * @param request  HTTP 请求对象
             * @param response HTTP 响应对象
             * @param chain    过滤器链
             * @throws ServletException Servlet 异常
             * @throws IOException      IO 异常
             */
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                // 登录接口必须允许用户用新凭据替换浏览器中已经过期的旧 token。
                if ("/admin/auth/login".equals(request.getRequestURI())) {
                    chain.doFilter(request, response);
                    return;
                }
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        var claims = jwtUtils.parseToken(token);
                        String username = claims.get("username", String.class);
                        var user = userMapper.selectByUsername(username);
                        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            return;
                        }
                        List<String> perms = userMapper.selectPermissionsByUserId(user.getId());
                        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());

                        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
                        if (perms != null) authorities.addAll(perms.stream()
                                .map(SimpleGrantedAuthority::new).toList());
                        if (roles != null) authorities.addAll(roles.stream()
                                .map(SimpleGrantedAuthority::new).toList());

                        var auth = new UsernamePasswordAuthenticationToken(
                            username, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (Exception ignored) {
                        // 过期或无效 token 明确返回 401，让前端清理登录态并回到登录页。
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }
}
