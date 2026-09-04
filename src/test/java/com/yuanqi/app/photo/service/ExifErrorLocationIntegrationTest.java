package com.yuanqi.app.photo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.AppApplication;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.vo.WorkViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AppApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExifErrorLocationIntegrationTest {
    private static final Path MEDIA_ROOT = Path.of(System.getProperty("java.io.tmpdir"),
            "2400px-exif-error-location-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", MEDIA_ROOT::toString);
        registry.add("app.media.worker-delay-ms", () -> "3600000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountMapper accounts;
    @Autowired AuthSessionService sessions;
    @Autowired WorkService works;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test void 第三项未来时间同时定位数组项公开媒体和字段() throws Exception {
        Fixture fixture = fixture("third_future");
        WorkRequests.Draft request = draft(fixture.mediaIds(), List.of(
                item(fixture.mediaIds().get(0), past()), item(fixture.mediaIds().get(1), past()),
                item(fixture.mediaIds().get(2), future())));

        JsonNode error = error(fixture, request);
        assertThat(error.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.at("/fieldErrors/0/path").asText())
                .isEqualTo("mediaParameters[2].parameters.captureTime");
        assertThat(error.at("/fieldErrors/0/code").asText()).isEqualTo("CAPTURE_TIME_IN_FUTURE");
        assertThat(error.at("/itemErrors/0/resourceId").asText()).isEqualTo(fixture.mediaIds().get(2));
        assertThat(error.at("/itemErrors/0/code").asText()).isEqualTo("INVALID_MEDIA_PARAMETERS");
        assertThat(error.path("fieldErrors")).hasSize(1);
        assertThat(error.path("itemErrors")).hasSize(1);
    }

    @Test void 第一和第三项同时失败且顺序稳定不覆盖() throws Exception {
        Fixture fixture = fixture("first_third");
        JsonNode error = error(fixture, draft(fixture.mediaIds(), List.of(
                item(fixture.mediaIds().get(0), future()), item(fixture.mediaIds().get(1), past()),
                item(fixture.mediaIds().get(2), future()))));

        assertThat(values(error.path("fieldErrors"), "path"))
                .containsExactly("mediaParameters[0].parameters.captureTime",
                        "mediaParameters[2].parameters.captureTime");
        assertThat(values(error.path("itemErrors"), "resourceId"))
                .containsExactly(fixture.mediaIds().get(0), fixture.mediaIds().get(2));
    }

    @Test void 同一项多个字段失败返回完整字段集合和单一条目定位() throws Exception {
        Fixture fixture = fixture("multi_field");
        WorkRequests.PhotoParameters invalid = new WorkRequests.PhotoParameters(
                OffsetDateTime.parse("2099-01-02T03:04:05+08:00"), "相".repeat(101), "Lens\nInjected",
                null, null, null, null);
        JsonNode error = error(fixture, draft(fixture.mediaIds(), List.of(
                item(fixture.mediaIds().get(0), past()), item(fixture.mediaIds().get(1), invalid),
                item(fixture.mediaIds().get(2), past()))));

        assertThat(values(error.path("fieldErrors"), "path"))
                .containsExactly("mediaParameters[1].parameters.captureTime",
                        "mediaParameters[1].parameters.cameraBody", "mediaParameters[1].parameters.lens");
        assertThat(values(error.path("fieldErrors"), "code"))
                .containsExactly("CAPTURE_TIME_IN_FUTURE", "MAX_GRAPHEME_LENGTH", "INVALID_TEXT");
        assertThat(error.path("itemErrors")).hasSize(1);
        assertThat(error.at("/itemErrors/0/resourceId").asText()).isEqualTo(fixture.mediaIds().get(1));
    }

    @Test void 嵌套BeanValidation沿用相同数组项和公开媒体定位() throws Exception {
        Fixture fixture = fixture("bean_nested");
        WorkRequests.MediaParameters missing = new WorkRequests.MediaParameters(fixture.mediaIds().get(1), null);
        JsonNode error = error(fixture, draft(fixture.mediaIds(), List.of(
                item(fixture.mediaIds().get(0), past()), missing, item(fixture.mediaIds().get(2), past()))));

        assertThat(error.at("/fieldErrors/0/path").asText()).isEqualTo("mediaParameters[1].parameters");
        assertThat(error.at("/itemErrors/0/resourceId").asText()).isEqualTo(fixture.mediaIds().get(1));
        assertThat(error.at("/itemErrors/0/code").asText()).isEqualTo("INVALID_MEDIA_PARAMETERS");
    }

    @Test void 普通顶层字段错误保持fieldErrors且不生成itemErrors() throws Exception {
        Fixture fixture = fixture("top_level");
        WorkRequests.Draft invalid = new WorkRequests.Draft("题".repeat(101), null, null, null, List.of(),
                fixture.mediaIds(), List.of(item(fixture.mediaIds().get(0), past())));
        JsonNode error = error(fixture, invalid);

        assertThat(error.at("/fieldErrors/0/path").asText()).isEqualTo("title");
        assertThat(error.path("itemErrors")).isEmpty();
    }

    @Test void 合法三媒体请求保存成功并返回新ETag() throws Exception {
        Fixture fixture = fixture("legal");
        WorkRequests.Draft valid = draft(fixture.mediaIds(), List.of(
                item(fixture.mediaIds().get(0), past()), item(fixture.mediaIds().get(1), past()),
                item(fixture.mediaIds().get(2), past())));

        mockMvc.perform(put("/api/v1/photos/{workId}/draft", fixture.created().summary().workId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.authorization())
                        .header(HttpHeaders.IF_MATCH, fixture.created().summary().versionTag())
                        .contentType("application/json").content(mapper.writeValueAsBytes(valid)))
                .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ETAG));
    }

    private JsonNode error(Fixture fixture, WorkRequests.Draft request) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/photos/{workId}/draft", fixture.created().summary().workId())
                        .header(HttpHeaders.AUTHORIZATION, fixture.authorization())
                        .header(HttpHeaders.IF_MATCH, fixture.created().summary().versionTag())
                        .contentType("application/json").content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private List<String> values(JsonNode array, String field) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    private Fixture fixture(String suffix) {
        long accountId = account("uid_error_" + suffix);
        List<String> mediaIds = List.of(media(accountId, "media_" + suffix + "_a"),
                media(accountId, "media_" + suffix + "_b"), media(accountId, "media_" + suffix + "_c"));
        WorkViews.AuthorWork created = works.create(accountId, draft(mediaIds, null));
        Account account = accounts.selectById(accountId);
        String authorization = "Bearer " + sessions.issue(account).view().accessToken();
        return new Fixture(mediaIds, created, authorization);
    }

    private WorkRequests.Draft draft(List<String> mediaIds, List<WorkRequests.MediaParameters> parameters) {
        return new WorkRequests.Draft(null, null, null, null, List.of(), mediaIds, parameters);
    }

    private WorkRequests.MediaParameters item(String mediaId, WorkRequests.PhotoParameters parameters) {
        return new WorkRequests.MediaParameters(mediaId, parameters);
    }

    private WorkRequests.PhotoParameters past() {
        return new WorkRequests.PhotoParameters(OffsetDateTime.parse("2020-08-29T11:59:59+08:00"),
                null, null, null, null, null, null);
    }

    private WorkRequests.PhotoParameters future() {
        return new WorkRequests.PhotoParameters(OffsetDateTime.parse("2099-01-02T03:04:05+08:00"),
                null, null, null, null, null, null);
    }

    private long account(String uid) {
        String email = uid + "@example.invalid";
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                uid, email, email, "test-hash", "USER");
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,UTC_TIMESTAMP(6))",
                id, uid);
        return id;
    }

    private String media(long owner, String mediaId) {
        jdbc.update("INSERT INTO media_asset(media_id,client_upload_id,owner_account_id,purpose,original_storage_key,web_storage_key,mime_type,byte_size,width,height,frame_count,status,row_version,created_at,updated_at) VALUES(?,?,?,'PHOTO',?,?,'image/jpeg',1024,1200,1200,1,'READY',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                mediaId, UUID.randomUUID().toString(), owner, "original/" + mediaId + ".jpg",
                "web/" + mediaId + ".jpg");
        return mediaId;
    }

    private record Fixture(List<String> mediaIds, WorkViews.AuthorWork created, String authorization) {
    }

}
