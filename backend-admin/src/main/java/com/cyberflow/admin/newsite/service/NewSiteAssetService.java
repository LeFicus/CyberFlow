package com.cyberflow.admin.newsite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.entity.NewSiteAsset;
import com.cyberflow.admin.newsite.mapper.NewSiteAssetMapper;
import com.cyberflow.admin.newsite.mapper.NewSiteMapper;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class NewSiteAssetService {
    private static final Set<String> ACTIVE = Set.of("queued", "generating");
    private static final int[] ICON_SIZES = {512, 192, 64, 32};

    private final NewSiteAssetMapper assetMapper;
    private final NewSiteMapper siteMapper;
    private final SysUserMapper userMapper;
    private final CrawlerConfigService configService;
    private final ImageGenerationClient imageClient;
    private final SiteAssetStorage storage;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20), runnable -> {
                Thread thread = new Thread(runnable, "new-site-image-generation");
                thread.setDaemon(true);
                return thread;
            });

    public NewSiteAssetService(NewSiteAssetMapper assetMapper, NewSiteMapper siteMapper,
                               SysUserMapper userMapper, CrawlerConfigService configService,
                               ImageGenerationClient imageClient, SiteAssetStorage storage) {
        this.assetMapper = assetMapper;
        this.siteMapper = siteMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.imageClient = imageClient;
        this.storage = storage;
    }

    @PostConstruct
    void recoverInterruptedJobs() {
        assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().in("status", ACTIVE)
                .set("status", "failed").set("error_message", "服务重启中断了生成任务，请重新生成"));
    }

    public List<NewSiteAsset> queueGeneration(Long siteId) {
        NewSite site = requireSite(siteId);
        long active = assetMapper.selectCount(new QueryWrapper<NewSiteAsset>()
                .eq("site_id", siteId).in("status", ACTIVE));
        if (active > 0) throw new IllegalArgumentException("该站点已有品牌素材正在生成，请等待完成");

        Map<String, Object> config = configService.getImageGenerationConfig(false);
        validateConfig(config);
        String group = UUID.randomUUID().toString();
        Long createdBy = currentUserId();
        List<NewSiteAsset> queued = List.of(
                createPlaceholder(site, group, "logo", "original", logoPrompt(site, config), createdBy),
                createPlaceholder(site, group, "banner", "desktop", bannerPrompt(site, config, false), createdBy),
                createPlaceholder(site, group, "banner", "mobile", bannerPrompt(site, config, true), createdBy)
        );
        try {
            executor.execute(() -> generateGroup(siteId, group));
        } catch (RejectedExecutionException e) {
            failGroup(group, "图像生成队列已满，请稍后重试");
            throw new IllegalArgumentException("图像生成队列已满，请稍后重试");
        }
        return list(siteId);
    }

    public Map<String, Object> imageConfig() {
        return configService.getImageGenerationConfig(true);
    }

    public Map<String, Object> updateImageConfig(Map<String, Object> body) {
        return configService.updateImageGenerationConfig(body == null ? Map.of() : body);
    }

    public void assertNoActiveGeneration(Long siteId) {
        long active = assetMapper.selectCount(new QueryWrapper<NewSiteAsset>()
                .eq("site_id", siteId).in("status", ACTIVE));
        if (active > 0) throw new IllegalArgumentException("品牌素材正在生成，完成后才能删除站点");
    }

    public List<NewSiteAsset> list(Long siteId) {
        requireSite(siteId);
        List<NewSiteAsset> assets = assetMapper.selectList(new QueryWrapper<NewSiteAsset>()
                .eq("site_id", siteId).orderByDesc("created_at").orderByDesc("id"));
        assets.forEach(this::decorate);
        return assets;
    }

    public NewSiteAsset requireReadyAsset(Long assetId) {
        NewSiteAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !"ready".equals(asset.getStatus())) throw new IllegalArgumentException("素材不存在或尚未生成完成");
        storage.resolve(asset.getStorageKey());
        decorate(asset);
        return asset;
    }

    public Path content(Long assetId) {
        return storage.resolve(requireReadyAsset(assetId).getStorageKey());
    }

    @Transactional
    public NewSiteAsset select(Long siteId, Long assetId) {
        requireSite(siteId);
        NewSiteAsset asset = requireReadyAsset(assetId);
        if (!siteId.equals(asset.getSiteId())) throw new IllegalArgumentException("素材不属于该站点");
        if ("icon".equals(asset.getAssetType())) throw new IllegalArgumentException("Icon 会随 Logo 自动选择，请选择对应 Logo");

        if ("logo".equals(asset.getAssetType())) {
            assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().eq("site_id", siteId)
                    .in("asset_type", List.of("logo", "icon")).set("is_selected", 0));
            assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().eq("site_id", siteId)
                    .eq("generation_group", asset.getGenerationGroup()).in("asset_type", List.of("logo", "icon"))
                    .eq("status", "ready").set("is_selected", 1));
        } else {
            assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().eq("site_id", siteId)
                    .eq("asset_type", asset.getAssetType()).eq("variant", asset.getVariant()).set("is_selected", 0));
            assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().eq("id", assetId).set("is_selected", 1));
        }
        return requireReadyAsset(assetId);
    }

    public List<AssetFile> selectedFiles(Long siteId) {
        NewSite site = requireSite(siteId);
        List<NewSiteAsset> selected = assetMapper.selectList(new QueryWrapper<NewSiteAsset>()
                .eq("site_id", siteId).eq("status", "ready").eq("is_selected", 1)
                .orderByAsc("asset_type").orderByAsc("variant"));
        boolean hasLogo = selected.stream().anyMatch(asset -> "logo".equals(asset.getAssetType()));
        boolean hasDesktop = selected.stream().anyMatch(asset -> "banner".equals(asset.getAssetType())
                && "desktop".equals(asset.getVariant()));
        boolean hasMobile = selected.stream().anyMatch(asset -> "banner".equals(asset.getAssetType())
                && "mobile".equals(asset.getVariant()));
        if (!hasLogo || !hasDesktop || !hasMobile) {
            throw new IllegalArgumentException("请先分别选择 Logo、桌面 Banner 和移动 Banner");
        }
        return selected.stream().map(asset -> new AssetFile(
                asset.getAssetType() + "-" + asset.getVariant() + ".png",
                storage.resolve(asset.getStorageKey()), asset.getMimeType(), site.getDomain())).toList();
    }

    public void deleteSiteFiles(Long siteId) {
        try {
            storage.deleteSite(siteId);
        } catch (IOException e) {
            log.warn("Unable to delete brand assets for site {}: {}", siteId, e.toString());
        }
    }

    private void generateGroup(Long siteId, String group) {
        NewSite site = siteMapper.selectById(siteId);
        if (site == null) {
            failGroup(group, "站点已被删除");
            return;
        }
        Map<String, Object> config = configService.getImageGenerationConfig(false);
        List<NewSiteAsset> assets = assetMapper.selectList(new QueryWrapper<NewSiteAsset>()
                .eq("generation_group", group).orderByAsc("id"));
        for (NewSiteAsset asset : assets) {
            if (Thread.currentThread().isInterrupted()) {
                fail(asset, "服务停止，生成任务已中断");
                continue;
            }
            try {
                generateOne(site, asset, config);
            } catch (Exception e) {
                log.warn("Brand asset generation failed: site={}, asset={}, error={}", siteId, asset.getId(), e.toString());
                fail(asset, message(e));
            }
        }
    }

    private void generateOne(NewSite site, NewSiteAsset asset, Map<String, Object> config) throws IOException {
        asset.setStatus("generating");
        asset.setErrorMessage(null);
        assetMapper.updateById(asset);
        boolean logo = "logo".equals(asset.getAssetType());
        String size = logo ? "1024x1024" : "mobile".equals(asset.getVariant()) ? "1024x1536" : "1536x1024";
        ImageGenerationClient.GeneratedImage generated = imageClient.generate(new ImageGenerationClient.Request(
                text(config.get("baseUrl")), text(config.get("apiKey")), text(config.get("model")),
                firstNonBlank(text(config.get("quality")), "medium"), asset.getPrompt(), size, logo));
        BufferedImage decoded = storage.decode(generated.bytes());
        BufferedImage prepared = logo ? square(decoded, 1024, 0.9)
                : "mobile".equals(asset.getVariant()) ? cover(decoded, 750, 1000) : cover(decoded, 1600, 640);
        SiteAssetStorage.StoredImage stored = storage.store(site.getId(), asset.getGenerationGroup(),
                asset.getAssetType() + "-" + asset.getVariant(), prepared);
        if (logo) deriveIcons(site, asset, prepared);
        ready(asset, stored);
    }

    private void deriveIcons(NewSite site, NewSiteAsset logo, BufferedImage source) throws IOException {
        for (int size : ICON_SIZES) {
            BufferedImage iconImage = square(source, size, 0.82);
            String variant = size + "x" + size;
            SiteAssetStorage.StoredImage stored = storage.store(site.getId(), logo.getGenerationGroup(), "icon-" + variant, iconImage);
            NewSiteAsset icon = baseAsset(site, logo.getGenerationGroup(), "icon", variant, logo.getPrompt(), logo.getCreatedBy());
            icon.setProvider("derived");
            icon.setModel(logo.getModel());
            icon.setStatus("ready");
            applyStored(icon, stored);
            assetMapper.insert(icon);
        }
    }

    private NewSiteAsset createPlaceholder(NewSite site, String group, String type, String variant,
                                           String prompt, Long createdBy) {
        NewSiteAsset asset = baseAsset(site, group, type, variant, prompt, createdBy);
        asset.setStatus("queued");
        assetMapper.insert(asset);
        return asset;
    }

    private NewSiteAsset baseAsset(NewSite site, String group, String type, String variant,
                                   String prompt, Long createdBy) {
        Map<String, Object> config = configService.getImageGenerationConfig(false);
        NewSiteAsset asset = new NewSiteAsset();
        asset.setSiteId(site.getId());
        asset.setGenerationGroup(group);
        asset.setAssetType(type);
        asset.setVariant(variant);
        asset.setProvider(firstNonBlank(text(config.get("provider")), "openai"));
        asset.setModel(text(config.get("model")));
        asset.setPrompt(prompt);
        asset.setIsSelected(0);
        asset.setCreatedBy(createdBy);
        return asset;
    }

    private void ready(NewSiteAsset asset, SiteAssetStorage.StoredImage stored) {
        applyStored(asset, stored);
        asset.setStatus("ready");
        asset.setErrorMessage(null);
        assetMapper.updateById(asset);
    }

    private void applyStored(NewSiteAsset asset, SiteAssetStorage.StoredImage stored) {
        asset.setStorageKey(stored.storageKey());
        asset.setMimeType(stored.mimeType());
        asset.setWidth(stored.width());
        asset.setHeight(stored.height());
    }

    private void fail(NewSiteAsset asset, String error) {
        asset.setStatus("failed");
        asset.setErrorMessage(error);
        assetMapper.updateById(asset);
    }

    private void failGroup(String group, String error) {
        assetMapper.update(null, new UpdateWrapper<NewSiteAsset>().eq("generation_group", group)
                .in("status", ACTIVE).set("status", "failed").set("error_message", error));
    }

    private void decorate(NewSiteAsset asset) {
        if ("ready".equals(asset.getStatus())) asset.setContentUrl("/admin/new-site/assets/" + asset.getId() + "/content");
    }

    private NewSite requireSite(Long siteId) {
        if (siteId == null || siteId <= 0) throw new IllegalArgumentException("新站点 ID 无效");
        NewSite site = siteMapper.selectById(siteId);
        if (site == null) throw new IllegalArgumentException("新站点不存在：" + siteId);
        return site;
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        SysUser user = userMapper.selectByUsername(authentication.getName());
        return user == null ? null : user.getId();
    }

    private static void validateConfig(Map<String, Object> config) {
        if (text(config.get("apiKey")).isBlank()) throw new IllegalStateException("未配置图像 AI API Key，请先保存图像 AI 配置");
        if (text(config.get("baseUrl")).isBlank()) throw new IllegalStateException("未配置图像 AI Base URL");
        if (text(config.get("model")).isBlank()) throw new IllegalStateException("未配置图像 AI 模型");
    }

    private static String logoPrompt(NewSite site, Map<String, Object> config) {
        return brief(site, config) + "\nCreate one square logo mark on a transparent background. "
                + "Use a bold simple silhouette with strong recognition at 32 pixels. No letters words or typography.";
    }

    private static String bannerPrompt(NewSite site, Map<String, Object> config, boolean mobile) {
        return brief(site, config) + "\nCreate a text-free ecommerce homepage hero background in "
                + (mobile ? "a vertical mobile composition" : "a wide desktop composition")
                + ". Show a tasteful category-relevant lifestyle scene. Leave calm negative space for HTML headline overlay. "
                + "Do not render text logos buttons badges prices or watermarks.";
    }

    private static String brief(NewSite site, Map<String, Object> config) {
        return firstNonBlank(text(config.get("stylePrompt")), CrawlerConfigService.DEFAULT_IMAGE_STYLE_PROMPT)
                + "\nStore domain: " + site.getDomain()
                + "\nStore positioning: " + site.getCustomCategory()
                + "\nMain product categories: " + site.getMainProductCategory()
                + "\nSupplement product categories: " + site.getSupplementProductCategory()
                + "\nBrand title for context only (do not draw it): " + site.getSiteTitle();
    }

    static BufferedImage cover(BufferedImage source, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int scaledWidth = Math.max(width, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(height, (int) Math.round(source.getHeight() * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        quality(graphics);
        graphics.drawImage(source, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight, null);
        graphics.dispose();
        return result;
    }

    static BufferedImage square(BufferedImage source, int size, double fill) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        quality(graphics);
        int content = Math.max(1, (int) Math.round(size * fill));
        double scale = Math.min((double) content / source.getWidth(), (double) content / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        graphics.drawImage(source, (size - width) / 2, (size - height) / 2, width, height, null);
        graphics.dispose();
        return result;
    }

    private static void quality(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static String message(Exception error) {
        String value = error.getMessage() == null ? "图像生成失败" : error.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String firstNonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }

    public record AssetFile(String name, Path path, String mimeType, String domain) {}
}
