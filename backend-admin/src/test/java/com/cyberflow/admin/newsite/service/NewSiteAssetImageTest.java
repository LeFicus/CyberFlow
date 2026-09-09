package com.cyberflow.admin.newsite.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewSiteAssetImageTest {
    @Test
    void bannerCoverProducesTheExactTemplateDimensions() {
        BufferedImage source = new BufferedImage(1024, 1536, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = NewSiteAssetService.cover(source, 1600, 640);
        assertEquals(1600, result.getWidth());
        assertEquals(640, result.getHeight());
    }

    @Test
    void iconResizeUsesATransparentSquareCanvas() {
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        BufferedImage result = NewSiteAssetService.square(source, 32, 0.8);
        assertEquals(32, result.getWidth());
        assertEquals(32, result.getHeight());
        assertEquals(0, (result.getRGB(0, 0) >>> 24) & 0xff);
    }
}
