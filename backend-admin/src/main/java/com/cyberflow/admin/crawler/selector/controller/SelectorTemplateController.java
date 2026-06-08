package com.cyberflow.admin.crawler.selector.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.service.SelectorTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/selector/template")
@RequiredArgsConstructor
public class SelectorTemplateController {

    private final SelectorTemplateService service;

    @GetMapping
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> list(@RequestParam(required = false) String platform) {
        return Result.ok(service.list(platform));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:list')")
    public Result<?> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> create(@RequestBody SelectorTemplate template) {
        return Result.ok(service.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:update')")
    public Result<?> update(@PathVariable Long id, @RequestBody SelectorTemplate template) {
        return Result.ok(service.update(id, template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('selector:template:delete')")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize("hasAuthority('selector:template:create')")
    public Result<?> clone(@PathVariable Long id) {
        return Result.ok(service.clone(id));
    }
}
