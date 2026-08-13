package com.cyberflow.admin.crawler.selector.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.service.SelectorTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 选择器模板管理 REST 控制器。
 * <p>
 * 提供选择器模板的增删改查及克隆功能的 HTTP 接口，
 * 支持按平台筛选模板列表，所有操作受权限控制。
 * </p>
 *
 * <h3>权限列表</h3>
 * <ul>
 *   <li>selector:template:list   - 查看模板列表和详情</li>
 *   <li>selector:template:create - 创建和克隆模板</li>
 *   <li>selector:template:update - 更新模板</li>
 *   <li>selector:template:delete - 删除模板</li>
 * </ul>
 *
 * @author CyberFlow
 */
@RestController
@RequestMapping("/admin/selector/template")
@RequiredArgsConstructor
public class SelectorTemplateController {

    /** 选择器模板业务服务 */
    private final SelectorTemplateService service;

    /**
     * 查询模板列表，支持按平台过滤。
     *
     * @param platform 平台标识，可选，用于过滤指定平台的模板
     * @return 模板列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> list(@RequestParam(required = false) String platform) {
        return Result.ok(service.list(platform));
    }

    /**
     * 根据 ID 获取模板详情。
     *
     * @param id 模板主键 ID
     * @return 模板实体
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 创建新的选择器模板。
     *
     * @param template 模板实体（JSON 请求体）
     * @return 创建后的模板实体
     */
    @PostMapping
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> create(@RequestBody SelectorTemplate template) {
        return Result.ok(service.create(template));
    }

    /**
     * 更新指定 ID 的选择器模板。
     *
     * @param id       模板主键 ID
     * @param template 更新的模板数据
     * @return 更新后的模板实体
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:update')")
    public Result<?> update(@PathVariable Long id, @RequestBody SelectorTemplate template) {
        return Result.ok(service.update(id, template));
    }

    /**
     * 删除指定 ID 的选择器模板。
     * <p>
     * 注意：系统模板（isSystem=1）不允许删除，由服务层控制。
     * </p>
     *
     * @param id 模板主键 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:delete')")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 克隆指定 ID 的选择器模板。
     * <p>
     * 创建一个原模板的副本，副本名称为 "原名称 (copy)"，isSystem 设为 0。
     * </p>
     *
     * @param id 原模板主键 ID
     * @return 克隆后的新模板实体
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> clone(@PathVariable Long id) {
        return Result.ok(service.clone(id));
    }
}
