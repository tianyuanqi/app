package com.yuanqi.app.photo.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** 媒体存储边界；测试可用替身确定性注入读写、移动、删除与枚举故障。 */
public interface StoragePort {
    String stage(String mediaId, InputStream input) throws IOException;
    Path safe(String key);
    String originalKey(String mediaId, String extension);
    String webKey(String mediaId);
    void move(String sourceKey, String targetKey) throws IOException;
    void delete(String key) throws IOException;
    boolean exists(String key) throws IOException;
    InputStream open(String key) throws IOException;
    OutputStream create(String key) throws IOException;
    List<StoredObject> list() throws IOException;

    record StoredObject(String key, long size, Instant lastModified) { }
}
