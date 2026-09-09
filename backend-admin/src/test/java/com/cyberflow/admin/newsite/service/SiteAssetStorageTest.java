package com.cyberflow.admin.newsite.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteAssetStorageTest {
    @TempDir Path directory;

    @Test
    void storesPngUnderTheConfiguredRootAndRejectsTraversal() throws Exception {
        SiteAssetStorage storage = new SiteAssetStorage(directory.toString());
        storage.initialize();
        var stored = storage.store(42L, "group-1", "logo", new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));

        assertEquals("image/png", stored.mimeType());
        assertTrue(storage.resolve(stored.storageKey()).startsWith(directory));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../../outside.png"));
    }
}
