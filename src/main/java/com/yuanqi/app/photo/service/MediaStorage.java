package com.yuanqi.app.photo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class MediaStorage {
    private final Path root;

    public MediaStorage(@Value("${app.upload.dir}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String stage(String mediaId, InputStream input) throws IOException {
        Path target = safe("staging/" + mediaId + ".upload");
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return root.relativize(target).toString();
    }

    public Path safe(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("非法存储键");
        return path;
    }

    public String originalKey(String mediaId, String extension) { return "original/" + mediaId + "." + extension; }
    public String webKey(String mediaId) { return "web/" + mediaId + ".jpg"; }

    public void move(String sourceKey, String targetKey) throws IOException {
        Path target = safe(targetKey); Files.createDirectories(target.getParent());
        Files.move(safe(sourceKey), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(String key) throws IOException { if (key != null) Files.deleteIfExists(safe(key)); }
}
