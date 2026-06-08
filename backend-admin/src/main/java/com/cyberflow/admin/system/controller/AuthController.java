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

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final SysMenuService menuService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

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
