package com.cyberflow.admin.newsite.service;

import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.entity.NewSiteAsset;
import com.cyberflow.admin.newsite.mapper.NewSiteAssetMapper;
import com.cyberflow.admin.newsite.mapper.NewSiteMapper;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewSiteAssetServiceTest {
    private NewSiteAssetMapper assets;
    private NewSiteMapper sites;
    private SiteAssetStorage storage;
    private NewSiteAssetService service;

    @BeforeEach
    void setUp() {
        assets = mock(NewSiteAssetMapper.class);
        sites = mock(NewSiteMapper.class);
        storage = mock(SiteAssetStorage.class);
        service = new NewSiteAssetService(assets, sites, mock(SysUserMapper.class),
                mock(CrawlerConfigService.class), mock(ImageGenerationClient.class), storage);
        NewSite site = new NewSite();
        site.setId(42L);
        site.setDomain("example.test");
        when(sites.selectById(42L)).thenReturn(site);
    }

    @Test
    void packDownloadRequiresLogoAndBothBannerLayouts() {
        when(assets.selectList(any())).thenReturn(List.of(selected("logo", "original"), selected("banner", "desktop")));
        var error = assertThrows(IllegalArgumentException.class, () -> service.selectedFiles(42L));
        assertEquals("请先分别选择 Logo、桌面 Banner 和移动 Banner", error.getMessage());
    }

    @Test
    void completeSelectionProducesAuthenticatedFilesWithoutExposingStorageKeys() {
        when(assets.selectList(any())).thenReturn(List.of(
                selected("logo", "original"), selected("banner", "desktop"), selected("banner", "mobile")));
        when(storage.resolve(any())).thenReturn(Path.of("/tmp/asset.png"));
        assertEquals(3, service.selectedFiles(42L).size());
    }

    private NewSiteAsset selected(String type, String variant) {
        NewSiteAsset asset = new NewSiteAsset();
        asset.setSiteId(42L);
        asset.setAssetType(type);
        asset.setVariant(variant);
        asset.setStatus("ready");
        asset.setIsSelected(1);
        asset.setStorageKey(type + "-" + variant + ".png");
        asset.setMimeType("image/png");
        return asset;
    }
}
