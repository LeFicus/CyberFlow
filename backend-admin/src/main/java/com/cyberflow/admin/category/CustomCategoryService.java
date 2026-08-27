package com.cyberflow.admin.category;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomCategoryService {
    private final JdbcTemplate jdbc;
    private static final Pattern PROHIBITED = Pattern.compile("保健品|保健|食品|枪支|枪械|弹药|武器|毒品|烟酒|烟草|烟具|酒精|服装|服饰|成人");
    public record Category(long id, long parentId, String name, boolean enabled, int sortOrder) {}
    public record Input(Long parentId, String name, Boolean enabled, Integer sortOrder) {}

    public List<Category> list() {
        return jdbc.query("SELECT * FROM custom_category ORDER BY sort_order,id", (r, n) ->
            new Category(r.getLong("id"), r.getLong("parent_id"), r.getString("name"), r.getBoolean("enabled"), r.getInt("sort_order")));
    }
    private Category require(long id, List<Category> all) {
        return all.stream().filter(c -> c.id == id).findFirst().orElseThrow(() -> new IllegalArgumentException("分类不存在"));
    }
    static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 100 || name.contains("|||") || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("分类名称须为 1–100 字，不能包含 ||| 或控制字符");
        if (PROHIBITED.matcher(name).find()) throw new IllegalArgumentException("该商品类目不允许使用");
        return name;
    }
    private boolean used(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM crawl_site_config WHERE category=?) OR EXISTS(SELECT 1 FROM scraped_data.ecommerce_products WHERE custom_category=?)", Boolean.class, name, name));
    }
    // Serializes catalog changes and source assignments without locking the product table.
    private void lock() { jdbc.queryForList("SELECT id FROM custom_category_seed WHERE id=1 FOR UPDATE"); }
    @Transactional
    public void save(Long id, Input input) {
        lock();
        String name = validateName(input.name());
        long parent = input.parentId() == null ? 0 : input.parentId();
        int sort = input.sortOrder() == null ? 0 : input.sortOrder();
        if (sort < 0 || sort > 100000) throw new IllegalArgumentException("排序范围为 0–100000");
        List<Category> all = list();
        Category old = id == null ? null : require(id, all);
        if (parent != 0 && (Objects.equals(id, parent) || require(parent, all).parentId != 0))
            throw new IllegalArgumentException("分类最多两级，不能选择自己或二级分类作为上级");
        if (old != null && parent != 0 && all.stream().anyMatch(c -> c.parentId == old.id))
            throw new IllegalArgumentException("请先移走子分类，再调整上级");
        if (old != null && !old.name.equals(name) && used(old.name))
            throw new IllegalArgumentException("该分类已被使用，不能改名；可停用后添加新分类");
        if (all.stream().anyMatch(c -> !Objects.equals(c.id, id) && c.name.equalsIgnoreCase(name)))
            throw new IllegalArgumentException("分类名称已存在");
        boolean enabled = input.enabled() == null || input.enabled();
        try {
            if (id == null) jdbc.update("INSERT INTO custom_category(parent_id,name,enabled,sort_order) VALUES(?,?,?,?)", parent,name,enabled,sort);
            else jdbc.update("UPDATE custom_category SET parent_id=?,name=?,enabled=?,sort_order=? WHERE id=?",parent,name,enabled,sort,id);
        } catch (DuplicateKeyException e) { throw new IllegalArgumentException("分类名称已存在"); }
    }
    @Transactional
    public void delete(long id) {
        lock();
        List<Category> all = list(); Category category = require(id, all);
        if (all.stream().anyMatch(c -> c.parentId == id)) throw new IllegalArgumentException("请先删除或移动子分类");
        if (used(category.name)) throw new IllegalArgumentException("该分类已被使用，请停用以保留历史关联");
        jdbc.update("DELETE FROM custom_category WHERE id=?", id);
    }
    @Transactional
    public void validateSelection(String name, String previous) {
        lock();
        if (name != null && name.equals(previous)) return; // An unchanged historical selection is retained.
        List<Category> all = list();
        Category category = all.stream().filter(c -> c.name.equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("请选择已维护的自定义分类"));
        if (!category.enabled || category.parentId != 0 && !require(category.parentId, all).enabled)
            throw new IllegalArgumentException("该分类已停用，请选择其他分类");
    }
}
