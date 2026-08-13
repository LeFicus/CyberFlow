package com.cyberflow.admin.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.entity.SysUserRole;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import com.cyberflow.admin.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统用户业务服务。
 * <p>
 * 继承 MyBatis-Plus 的 {@link ServiceImpl}，提供用户的 CRUD 操作。
 * 同时实现 Spring Security 的 {@link UserDetailsService}，用于认证时加载用户信息。
 * 包含密码加密、角色分配和权限查询等业务逻辑。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> implements UserDetailsService {

    /** 用户 Mapper，用于执行自定义 SQL（角色、权限查询） */
    private final SysUserMapper userMapper;

    /** 用户-角色关联 Mapper，用于角色分配操作 */
    private final SysUserRoleMapper userRoleMapper;

    /** 密码编码器，用于密码加密和校验 */
    private final PasswordEncoder passwordEncoder;

    /**
     * Spring Security 认证时加载用户信息。
     * <p>
     * 根据用户名查询用户，若用户不存在或已被禁用则抛出异常。
     * 查询出用户关联的角色和权限后，构建 Spring Security 的 {@link UserDetails} 对象。
     * </p>
     *
     * @param username 登录用户名
     * @return UserDetails 对象，包含用户名、密码和权限集合
     * @throws UsernameNotFoundException 当用户不存在或已被禁用时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = userMapper.selectPermissionsByUserId(user.getId());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .authorities(perms.toArray(new String[0]))
                .build();
    }

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户实体，若不存在则返回 null
     */
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    /**
     * 创建新用户（密码会在保存前加密）。
     *
     * @param user 新用户实体，密码为明文
     * @return true 表示创建成功
     */
    @Transactional
    public boolean createUser(SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return save(user);
    }

    /**
     * 更新用户信息。
     * <p>
     * 若传入的密码非空且非空白，则加密后更新；否则不修改密码字段。
     * </p>
     *
     * @param user 更新后的用户实体
     * @return true 表示更新成功
     */
    @Transactional
    public boolean updateUser(SysUser user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        return updateById(user);
    }

    /**
     * 为用户分配角色。
     * <p>
     * 先删除用户原有的所有角色关联，再批量插入新的角色关联。
     * 整个过程在同一个事务中执行。
     * </p>
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表，可为空或 null（清空所有角色）
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds != null) {
            roleIds.forEach(roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            });
        }
    }
}
