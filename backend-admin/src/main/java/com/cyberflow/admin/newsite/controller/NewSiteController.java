package com.cyberflow.admin.newsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.entity.NewSiteAsset;
import com.cyberflow.admin.newsite.model.NewSiteBatchCreateRequest;
import com.cyberflow.admin.newsite.model.NewSiteStatusRequest;
import com.cyberflow.admin.newsite.service.NewSiteService;
import com.cyberflow.admin.newsite.service.NewSiteAssetService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/admin/new-site")
@RequiredArgsConstructor
public class NewSiteController {

    private final NewSiteService newSiteService;
    private final NewSiteAssetService assetService;

    @GetMapping
    @PreAuthorize("hasAuthority('newsite:list')")
    public Result<Page<NewSite>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword) {
        return Result.ok(newSiteService.page(page, size, status, keyword));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('newsite:list')")
    public Result<Map<String, Object>> options() {
        return Result.ok(newSiteService.options());
    }

    @GetMapping("/ai-config")
    @PreAuthorize("hasAnyAuthority('newsite:list', 'newsite:config')")
    public Result<Map<String, Object>> aiConfig() {
        return Result.ok(newSiteService.aiConfig());
    }

    @PutMapping("/ai-config")
    @PreAuthorize("hasAuthority('newsite:config')")
    public Result<Map<String, Object>> updateAiConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(newSiteService.updateAiConfig(body));
    }

    @GetMapping("/image-ai-config")
    @PreAuthorize("hasAnyAuthority('newsite:list', 'newsite:config')")
    public Result<Map<String, Object>> imageAiConfig() {
        return Result.ok(assetService.imageConfig());
    }

    @PutMapping("/image-ai-config")
    @PreAuthorize("hasAuthority('newsite:config')")
    public Result<Map<String, Object>> updateImageAiConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(assetService.updateImageConfig(body));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('newsite:create')")
    public Result<?> createBatch(@Valid @RequestBody NewSiteBatchCreateRequest request) {
        return Result.ok(newSiteService.createBatch(request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('newsite:status')")
    public Result<NewSite> updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody NewSiteStatusRequest request) {
        return Result.ok(newSiteService.updateStatus(id, request.getStatus()));
    }

    @GetMapping("/{siteId}/assets")
    @PreAuthorize("hasAuthority('newsite:list')")
    public Result<List<NewSiteAsset>> assets(@PathVariable Long siteId) {
        return Result.ok(assetService.list(siteId));
    }

    @PostMapping("/{siteId}/assets/generate")
    @PreAuthorize("hasAuthority('newsite:asset')")
    public Result<List<NewSiteAsset>> generateAssets(@PathVariable Long siteId) {
        return Result.ok(assetService.queueGeneration(siteId));
    }

    @PutMapping("/{siteId}/assets/{assetId}/select")
    @PreAuthorize("hasAuthority('newsite:asset')")
    public Result<NewSiteAsset> selectAsset(@PathVariable Long siteId, @PathVariable Long assetId) {
        return Result.ok(assetService.select(siteId, assetId));
    }

    @GetMapping("/assets/{assetId}/content")
    @PreAuthorize("hasAnyAuthority('newsite:list', 'newsite:asset')")
    public void assetContent(@PathVariable Long assetId,
                             @RequestParam(defaultValue = "false") boolean download,
                             HttpServletResponse response) throws IOException {
        NewSiteAsset asset = assetService.requireReadyAsset(assetId);
        var path = assetService.content(assetId);
        response.setContentType(asset.getMimeType() == null ? "image/png" : asset.getMimeType());
        response.setContentLengthLong(Files.size(path));
        response.setHeader("Cache-Control", "private, max-age=300");
        response.setHeader("Content-Disposition", (download ? "attachment" : "inline")
                + "; filename=\"" + asset.getAssetType() + "-" + asset.getVariant() + ".png\"");
        try (var input = Files.newInputStream(path)) {
            input.transferTo(response.getOutputStream());
        }
    }

    @GetMapping("/{siteId}/assets/download")
    @PreAuthorize("hasAnyAuthority('newsite:list', 'newsite:asset')")
    public void downloadAssets(@PathVariable Long siteId, HttpServletResponse response) throws IOException {
        List<NewSiteAssetService.AssetFile> files = assetService.selectedFiles(siteId);
        String domain = files.get(0).domain().replaceAll("[^a-zA-Z0-9.-]", "_");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + domain + "-brand-assets.zip\"");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (NewSiteAssetService.AssetFile file : files) {
                zip.putNextEntry(new ZipEntry(file.name()));
                Files.copy(file.path(), zip);
                zip.closeEntry();
            }
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('newsite:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.assertNoActiveGeneration(id);
        newSiteService.delete(id);
        assetService.deleteSiteFiles(id);
        return Result.ok();
    }
}
