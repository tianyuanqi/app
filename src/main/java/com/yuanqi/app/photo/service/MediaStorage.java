package com.yuanqi.app.photo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class MediaStorage implements StoragePort {
    private final Path root;

    public MediaStorage(@Value("${app.upload.dir}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override public String stage(String mediaId, InputStream input) throws IOException {
        Path target = safe("staging/" + mediaId + ".upload");
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        return root.relativize(target).toString();
    }

    @Override public Path safe(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("非法存储键");
        return path;
    }

    @Override public String originalKey(String mediaId, String extension) { return "original/" + mediaId + "." + extension; }
    @Override public String webKey(String mediaId) { return "web/" + mediaId + ".jpg"; }

    @Override public void move(String sourceKey, String targetKey) throws IOException {
        if (sourceKey.equals(targetKey)) return;
        Path target = safe(targetKey); Files.createDirectories(target.getParent());
        Files.move(safe(sourceKey), target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override public void delete(String key) throws IOException { if (key != null) Files.deleteIfExists(safe(key)); }
    @Override public boolean exists(String key) { return key != null && Files.isRegularFile(safe(key)); }
    @Override public InputStream open(String key) throws IOException { return Files.newInputStream(safe(key)); }
    @Override public OutputStream create(String key) throws IOException {
        Path target = safe(key); Files.createDirectories(target.getParent()); return Files.newOutputStream(target);
    }
    @Override public List<StoredObject> list() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).map(path -> {
                try {
                    return new StoredObject(root.relativize(path).toString(), Files.size(path),
                            Files.getLastModifiedTime(path).toInstant());
                } catch (IOException e) { throw new StorageWalkException(e); }
            }).toList();
        } catch (StorageWalkException e) { throw e.cause; }
    }

    private static final class StorageWalkException extends RuntimeException {
        private final IOException cause;
        private StorageWalkException(IOException cause) { super(cause); this.cause = cause; }
    }
}
