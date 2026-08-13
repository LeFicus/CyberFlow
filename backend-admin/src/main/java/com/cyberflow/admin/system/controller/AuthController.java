package com.cyberflow.admin.system.controller;

import com.cyberflow.admin.common.JwtUtils;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.service.SysMenuService;
import com.cyberflow.admin.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证控制器。
 * <p>
 * 处理用户登录和获取当前用户信息的请求。登录成功后签发 JWT 令牌，
 * 返回给前端存储，后续请求通过 Authorization 头携带令牌进行身份认证。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 用户服务，用于查询用户信息和加载权限 */
    private final SysUserService userService;

    /** 菜单服务，用于获取当前用户的菜单树 */
    private final SysMenuService menuService;

    /** JWT 工具，用于生成令牌 */
    private final JwtUtils jwtUtils;

    /** 密码编码器，用于验证登录密码 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录接口。
     * <p>
     * 接收用户名和密码，验证通过后生成 JWT 令牌并返回用户基本信息、
     * 角色列表和权限列表。密码使用 BCrypt 算法进行校验。
     * </p>
     *
     * @param body 请求体，包含 {@code username} 和 {@code password} 两个键
     * @return 包含 token 和 userInfo 的成功响应：
     *         <ul>
     *           <li>token - JWT 令牌字符串</li>
     *           <li>userInfo - 包含 id、username、nickname、roles、permissions 的 Map</li>
     *         </ul>
     * @throws BadCredentialsException 当用户名不存在、用户被禁用或密码不匹配时抛出
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUser user = userService.getByUsername(username);
        if (user == null || user.getStatus() == 0) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        List<String> roles = userService.getBaseMapper().selectRoleCodesByUserId(user.getId());
        List<String> perms = userService.getBaseMapper().selectPermissionsByUserId(user.getId());

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), roles, perms);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("roles", roles);
        userInfo.put("permissions", perms);
        result.put("userInfo", userInfo);

        return Result.ok(result);
    }

    /**
     * 获取当前登录用户信息接口。
     * <p>
     * 从 Spring Security 上下文中获取当前认证用户，查询其详细信息、
     * 角色列表、权限列表和菜单树，用于前端动态渲染路由和权限控制。
     * </p>
     *
     * @return 包含 id、username、nickname、roles、permissions、menus 的成功响应
     */
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        SysUser user = userService.getByUsername(username);
        if (user == null) {
            return Result.fail(401, "用户不存在");
        }

        List<String> roles = userService.getBaseMapper().selectRoleCodesByUserId(user.getId());
        List<String> perms = userService.getBaseMapper().selectPermissionsByUserId(user.getId());
        var menus = menuService.getUserMenus(user.getId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("roles", roles);
        userInfo.put("permissions", perms);
        userInfo.put("menus", menus);

        return Result.ok(userInfo);
    }
}
