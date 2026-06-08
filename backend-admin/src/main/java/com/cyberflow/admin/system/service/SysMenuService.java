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

@Service
@RequiredArgsConstructor
public class SysMenuService extends ServiceImpl<SysMenuMapper, SysMenu> {

    private final SysMenuMapper menuMapper;

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

    private void buildTree(SysMenu parent, Map<Long, List<SysMenu>> childrenMap) {
        List<SysMenu> children = childrenMap.getOrDefault(parent.getId(), new ArrayList<>());
        children.sort(Comparator.comparing(SysMenu::getSortOrder));
        children.forEach(c -> buildTree(c, childrenMap));
        parent.setChildren(children);
    }

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
