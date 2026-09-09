package com.cyberflow.admin.newsite.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Iterator;

@Component
public class SiteAssetStorage {
    private final Path root;

    public SiteAssetStorage(@Value("${cyberflow.site-assets.directory:.data/site-assets}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    public StoredImage store(Long siteId, String group, String name, BufferedImage image) throws IOException {
        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path directory = root.resolve("site-" + siteId).resolve(group).normalize();
        if (!directory.startsWith(root)) throw new IOException("非法素材存储路径");
        Files.createDirectories(directory);
        Path target = directory.resolve(safeName + ".png");
        Path temp = directory.resolve(safeName + ".png.tmp");
        try (var output = Files.newOutputStream(temp)) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("当前环境无法编码 PNG 图片");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new StoredImage(root.relativize(target).toString(), "image/png", image.getWidth(), image.getHeight());
    }

    public BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > ImageGenerationClient.MAX_IMAGE_BYTES) {
            throw new IOException("生成图片为空或超过 25MB 限制");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("生成结果不是有效图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 40_000_000L) {
                    throw new IOException("生成图片像素尺寸过大");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("生成结果不是有效图片");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("素材文件不存在");
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException("素材文件不存在");
        return path;
    }

    public byte[] png(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public void deleteSite(Long siteId) throws IOException {
        Path siteDirectory = root.resolve("site-" + siteId).normalize();
        if (!siteDirectory.startsWith(root) || !Files.exists(siteDirectory)) return;
        try (var paths = Files.walk(siteDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    public record StoredImage(String storageKey, String mimeType, int width, int height) {}
}
