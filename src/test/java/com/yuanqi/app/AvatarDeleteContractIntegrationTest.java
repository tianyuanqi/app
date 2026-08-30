package com.yuanqi.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.service.MediaStorage;
import com.yuanqi.app.photo.service.WorkService;
import com.yuanqi.app.photo.vo.WorkViews;
import com.yuanqi.app.user.service.AvatarService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvatarDeleteContractIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "2400px-be009-media-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", MEDIA_ROOT::toString);
        registry.add("app.media.worker-delay-ms", () -> "3600000");
        registry.add("app.media.cleanup-delay-ms", () -> "3600000");
        registry.add("app.media.reconcile-delay-ms", () -> "3600000");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountMapper accounts;
    @Autowired AuthSessionService sessions;
    @Autowired WorkService works;
    @Autowired MediaStorage storage;
    @Autowired AvatarService avatars;

    @AfterAll
    static void cleanupMediaRoot() throws Exception {
        if (!Files.exists(MEDIA_ROOT)) return;
        try (var paths = Files.walk(MEDIA_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void 前端等价512PNG上传后仅保留512头像且返回新ETag() throws Exception {
        long accountId = account("avatar", "USER");
        String authorization = token(accountId);
        String before = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        byte[] responseBytes = mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", image("png", 512, 512)))
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(HttpHeaders.IF_MATCH, before)
                        .header("Idempotency-Key", "avatar-upload-0001"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.ETAG, org.hamcrest.Matchers.not(before)))
                .andExpect(jsonPath("$.data.avatar.width").value(512))
                .andExpect(jsonPath("$.data.avatar.height").value(512))
                .andExpect(jsonPath("$.data.profileVersionTag").isString())
                .andReturn().getResponse().getContentAsByteArray();

        JsonNode data = mapper.readTree(responseBytes).path("data");
        String mediaId = data.at("/avatar/mediaId").asText();
        String url = data.at("/avatar/url").asText();
        byte[] web = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(web));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
        assertThat(jdbc.queryForMap("SELECT purpose,original_storage_key,web_storage_key,width,height,status FROM media_asset WHERE media_id=?", mediaId))
                .containsEntry("purpose", "AVATAR")
                .containsEntry("original_storage_key", null)
                .containsEntry("width", 512)
                .containsEntry("height", 512)
                .containsEntry("status", "READY");
        String key = jdbc.queryForObject("SELECT web_storage_key FROM media_asset WHERE media_id=?", String.class, mediaId);
        assertThat(Files.isRegularFile(storage.safe(key))).isTrue();
    }

    @Test
    void JPEG_PNG_WebP与10MB边界保持既有约束且不保存原始上传() throws Exception {
        long accountId = account("avatar_formats", "USER");
        AvatarService.Mutation jpeg = avatars.upload(accountId, "\"profile-0\"",
                new MockMultipartFile("file", "avatar.jpg", "image/jpeg", image("jpeg", 512, 512)));
        AvatarService.Mutation png = avatars.upload(accountId, jpeg.profileVersionTag(),
                new MockMultipartFile("file", "avatar.png", "image/png", image("png", 512, 512)));
        byte[] onePixelWebp = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        AvatarService.Mutation webp = avatars.upload(accountId, png.profileVersionTag(),
                new MockMultipartFile("file", "avatar.webp", "image/webp", onePixelWebp));

        byte[] exactLimit = Arrays.copyOf(image("png", 512, 512), 10 * 1024 * 1024);
        AvatarService.Mutation boundary = avatars.upload(accountId, webp.profileVersionTag(),
                new MockMultipartFile("file", "avatar-limit.png", "image/png", exactLimit));
        int beforeRejected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND purpose='AVATAR'", Integer.class, accountId);
        assertThatThrownBy(() -> avatars.upload(accountId, boundary.profileVersionTag(),
                new MockMultipartFile("file", "too-large.png", "image/png", new byte[10 * 1024 * 1024 + 1])))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND purpose='AVATAR'", Integer.class, accountId))
                .isEqualTo(beforeRejected);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND purpose='AVATAR' AND original_storage_key IS NOT NULL",
                Integer.class, accountId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM media_asset WHERE owner_account_id=? AND purpose='AVATAR' AND width=512 AND height=512",
                Integer.class, accountId)).isEqualTo(4);
    }

    @Test
    void 管理员彻底删除返回固定DTO并可带ETag幂等重放且立即撤权() throws Exception {
        long ownerId = account("delete_owner", "USER");
        long adminId = account("delete_admin", "ADMIN");
        String mediaId = media(ownerId, "delete");
        WorkViews.AuthorWork created = works.create(ownerId,
                new WorkRequests.Draft("待删除作品", null, null, null, List.of(), List.of(mediaId)));
        String workId = created.summary().workId();
        String adminToken = token(adminId);
        String ownerToken = token(ownerId);
        String before = mockMvc.perform(get("/api/v1/moderation/photos/{workId}", workId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        String request = "{\"reason\":\"违规内容\",\"confirmation\":true}";

        byte[] first = mockMvc.perform(delete("/api/v1/moderation/photos/{workId}", workId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .header(HttpHeaders.IF_MATCH, before)
                        .header("Idempotency-Key", "admin-delete-0001")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, before))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.workId").value(workId))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.deletedAt").isString())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] replay = mockMvc.perform(delete("/api/v1/moderation/photos/{workId}", workId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .header(HttpHeaders.IF_MATCH, before)
                        .header("Idempotency-Key", "admin-delete-0001")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, before))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(replay).isEqualTo(first);
        mockMvc.perform(get("/api/v1/photos/{workId}/author-view", workId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM photo_work WHERE work_id=?", Integer.class, workId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM photo_tombstone WHERE work_id=?", Integer.class, workId)).isEqualTo(1);
    }

    private long account(String suffix, String role) {
        String uid = "u9_" + suffix.substring(0, Math.min(suffix.length(), 10)) + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        String email = uid + "@example.invalid";
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,'ACTIVE',0,NOW(6),NOW(6))", uid, email, email, "test-hash", role);
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,NOW(6))", id, uid);
        return id;
    }

    private String token(long accountId) {
        Account account = accounts.selectById(accountId);
        return "Bearer " + sessions.issue(account).view().accessToken();
    }

    private String media(long ownerId, String suffix) {
        String mediaId = "m_be009_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO media_asset(media_id,client_upload_id,owner_account_id,purpose,original_storage_key,web_storage_key,mime_type,byte_size,width,height,frame_count,status,row_version,created_at,updated_at) " +
                        "VALUES(?,?,?,'PHOTO',?,?,'image/jpeg',1024,512,512,1,'READY',0,NOW(6),NOW(6))",
                mediaId, UUID.randomUUID().toString(), ownerId, "original/" + mediaId + ".jpg", "web/" + mediaId + ".jpg");
        return mediaId;
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(32, 96, 160));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
}
