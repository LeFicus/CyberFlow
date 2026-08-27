package com.cyberflow.admin.category;

import com.cyberflow.admin.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/custom-categories")
@RequiredArgsConstructor
public class CustomCategoryController {
    private final CustomCategoryService service;
    @GetMapping
    @PreAuthorize("hasAnyAuthority('category:list','dashboard:product:view','crawler:site:config:list','crawler:site:config:create')")
    public Result<?> list() { return Result.ok(service.list()); }
    @PostMapping
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<?> create(@RequestBody CustomCategoryService.Input body) { service.save(null, body); return Result.ok(); }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<?> update(@PathVariable Long id, @RequestBody CustomCategoryService.Input body) { service.save(id,body); return Result.ok(); }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    public Result<?> delete(@PathVariable Long id) { service.delete(id); return Result.ok(); }
}
