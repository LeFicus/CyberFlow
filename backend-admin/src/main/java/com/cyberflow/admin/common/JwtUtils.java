package com.cyberflow.admin.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT（JSON Web Token）工具类。
 * <p>
 * 负责 JWT 的生成、解析和过期校验。密钥和过期时间通过配置文件注入。
 * Token 中包含用户 ID（sub）、用户名、角色列表和权限列表等自定义声明。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Component
public class JwtUtils {

    /** HMAC 签名密钥，用于令牌签名和验证 */
    private final SecretKey key;

    /** 令牌过期时间（毫秒） */
    private final long expiration;

    /**
     * 构造器注入 JWT 配置参数。
     *
     * @param secret     JWT 签名密钥，从配置文件 {@code cyberflow.jwt.secret} 读取
     * @param expiration JWT 过期时间（毫秒），从配置文件 {@code cyberflow.jwt.expiration} 读取
     */
    public JwtUtils(@Value("${cyberflow.jwt.secret}") String secret,
                    @Value("${cyberflow.jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 生成 JWT 令牌。
     * <p>
     * 令牌中包含用户 ID（作为 subject）、用户名、角色列表和权限列表作为自定义声明。
     * </p>
     *
     * @param userId      用户唯一标识
     * @param username    用户名
     * @param roles       角色编码列表
     * @param permissions 权限标识列表
     * @return 签发的 JWT 字符串
     */
    public String generateToken(Long userId, String username, List<String> roles, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "username", username,
                        "roles", roles,
                        "perms", permissions
                ))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 JWT 令牌，提取其中的声明信息。
     * <p>
     * 若 Token 无效、过期或签名不匹配，将抛出异常。
     * </p>
     *
     * @param token JWT 令牌字符串
     * @return 解析后的 Claims（声明）对象，包含 subject 和自定义声明
     * @throws io.jsonwebtoken.JwtException 当 Token 无效、过期或签名不匹配时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断 JWT 令牌是否已过期。
     * <p>
     * 通过尝试解析令牌来判断：若解析成功则未过期，解析失败（抛出任意异常）则认为已过期。
     * </p>
     *
     * @param token JWT 令牌字符串
     * @return true 表示已过期或无效，false 表示有效且未过期
     */
    public boolean isTokenExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
