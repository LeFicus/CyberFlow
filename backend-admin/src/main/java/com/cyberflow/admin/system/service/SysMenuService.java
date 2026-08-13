package com.cyberflow.admin.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyberflow.admin.system.entity.SysMenu;
import com.cyberflow.admin.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统菜单/权限业务服务。
 * <p>
 * 继承 MyBatis-Plus 的 {@link ServiceImpl}，提供菜单的 CRUD 操作。
 * 核心功能包括菜单树的构建和用户可见菜单的查询。
 * </p>
 *
 * @author CyberFlow Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SysMenuService extends ServiceImpl<SysMenuMapper, SysMenu> {

    /** 菜单 Mapper，用于执行自定义 SQL（按用户查询菜单） */
    private final SysMenuMapper menuMapper;

    /**
     * 获取完整的菜单树结构。
     * <p>
     * 查询所有菜单，按 parentId 进行分组递归构建树形结构，
     * 同级节点按 sortOrder 升序排列。
     * </p>
     *
     * @return 菜单树（顶级节点列表）
     */
    public List<SysMenu> getMenuTree() {
        List<SysMenu> all = list();
        Map<Long, List<SysMenu>> childrenMap = all.stream()
                .filter(m -> m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        return all.stream()
                .filter(m -> m.getParentId() == 0)
                .peek(m -> buildTree(m, childrenMap))
                .sorted(Comparator.comparing(SysMenu::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * 递归构建菜单子树。
     *
     * @param parent      父级菜单节点
     * @param childrenMap 按 parentId 分组的子菜单 Map
     */
    private void buildTree(SysMenu parent, Map<Long, List<SysMenu>> childrenMap) {
        List<SysMenu> children = childrenMap.getOrDefault(parent.getId(), new ArrayList<>());
        children.sort(Comparator.comparing(SysMenu::getSortOrder));
        children.forEach(c -> buildTree(c, childrenMap));
        parent.setChildren(children);
    }

    /**
     * 查询指定用户有权限访问的菜单树。
     * <p>
     * 仅返回状态为启用（status=1）且类型为目录或菜单（menuType in 0,1）的菜单项，
     * 按 sortOrder 排序后构建树形结构，用于前端动态路由生成。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户可见的菜单树
     */
    public List<SysMenu> getUserMenus(Long userId) {
        List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
        Map<Long, List<SysMenu>> childrenMap = menus.stream()
                .filter(m -> m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        return menus.stream()
                .filter(m -> m.getParentId() == 0)
                .peek(m -> buildTree(m, childrenMap))
                .sorted(Comparator.comparing(SysMenu::getSortOrder))
                .collect(Collectors.toList());
    }
}
