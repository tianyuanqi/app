package com.yuanqi.app.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanqi.app.common.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test void ADR0006读取与Mutation并发幂等契约进入运行时OpenAPI() throws Exception {
        JsonNode root = mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEtagRead(root, "/api/v1/admin/users/{uid}");
        assertEtagRead(root, "/api/v1/moderation/photos/{workId}");
        assertThat(successSchema(root, "/api/v1/admin/users/{uid}", "get"))
                .isNotEqualTo(successSchema(root, "/api/v1/moderation/photos/{workId}", "get"));
        assertMutation(root, "/api/v1/admin/users/{uid}/disable", "post");
        assertMutation(root, "/api/v1/moderation/photos/{workId}/revisions/{revisionId}/approve", "post");

        List<String> codes = new ArrayList<>();
        root.at("/components/schemas/ErrorResult/properties/code/enum")
                .forEach(value -> codes.add(value.asText()));
        assertThat(codes).contains("IDEMPOTENCY_KEY_REUSED", "INVALID_IF_MATCH")
                .doesNotContain("SELF_REVIEW_NOT_ALLOWED", "SELF_INTERACTION_NOT_ALLOWED");
    }

    @Test void 五十一个Operation全部使用逐状态受限ErrorCodeSchema() throws Exception {
        JsonNode root = openApi();
        int operations = 0;
        int errorResponses = 0;
        Set<String> refs = new HashSet<>();
        for (var paths = root.path("paths").fields(); paths.hasNext();) {
            var path = paths.next();
            for (var methods = path.getValue().fields(); methods.hasNext();) {
                var method = methods.next();
                if (!Set.of("get", "post", "put", "delete", "patch").contains(method.getKey())) continue;
                operations++;
                for (var responses = method.getValue().path("responses").fields(); responses.hasNext();) {
                    var response = responses.next();
                    if (!response.getKey().matches("[45][0-9]{2}")) continue;
                    errorResponses++;
                    String ref = response.getValue().at("/content/application~1json/schema/$ref").asText();
                    assertThat(ref).as("%s %s -> %s", method.getKey(), path.getKey(), response.getKey())
                            .startsWith("#/components/schemas/ErrorResult_");
                    assertThat(refs.add(ref)).as("每个 Operation/status 应使用独立 Schema: %s", ref).isTrue();
                    JsonNode enums = root.at(ref.substring(1) + "/properties/code/enum");
                    assertThat(enums.isArray()).isTrue();
                    assertThat(enums).isNotEmpty();
                    for (JsonNode value : enums) {
                        assertThat(ErrorCode.valueOf(value.asText()).getHttpStatus().value())
                                .isEqualTo(Integer.parseInt(response.getKey()));
                    }
                }
            }
        }
        assertThat(operations).isEqualTo(51);
        assertThat(errorResponses).isEqualTo(refs.size());
        assertCodes(root, "/api/v1/photos/{workId}/like", "put", "403", "ACCOUNT_UNAVAILABLE");
        assertCodes(root, "/api/v1/moderation/photos/{workId}/revisions/{revisionId}/approve", "post", "403",
                "ACCOUNT_UNAVAILABLE", "FORBIDDEN");
        assertThat(root.at("/paths/~1api~1v1~1auth~1logout/post/responses").has("401")).isFalse();
    }

    @Test void 关键消费者DTOSchema约束机器可读且无名称碰撞() throws Exception {
        JsonNode root = openApi();
        JsonNode schemas = root.at("/components/schemas");
        assertThat(schemas.has("SendCodeRequest")).isTrue();
        assertThat(schemas.at("/RegisterRequest/properties/password/minLength").asInt()).isEqualTo(8);
        assertThat(schemas.at("/RegisterRequest/properties/password/maxLength").asInt()).isEqualTo(64);
        assertThat(schemas.at("/RegisterRequest/properties/verificationCode/pattern").asText()).isEqualTo("^[0-9]{6}$");
        assertThat(schemas.at("/UpdateProfileRequest/properties/username/maxLength").asInt()).isEqualTo(20);
        assertThat(schemas.at("/UpdateProfileRequest/properties/bio/maxLength").asInt()).isEqualTo(200);
        assertThat(schemas.at("/WorkDraftRequest/properties/title/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/WorkDraftRequest/properties/description/maxLength").asInt()).isEqualTo(5000);
        assertThat(schemas.at("/WorkDraftRequest/properties/location/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/WorkDraftRequest/properties/tags/maxItems").asInt()).isEqualTo(5);
        assertThat(schemas.at("/WorkDraftRequest/properties/tags/items/maxLength").asInt()).isEqualTo(20);
        assertThat(schemas.at("/WorkDraftRequest/properties/mediaIds/minItems").asInt()).isEqualTo(1);
        assertThat(schemas.at("/WorkDraftRequest/properties/mediaIds/maxItems").asInt()).isEqualTo(9);
        assertThat(schemas.at("/WorkDraftRequest/properties/mediaParameters/maxItems").asInt()).isEqualTo(9);
        assertThat(schemas.at("/WorkDraftRequest/properties/mediaParameters/items/$ref").asText())
                .endsWith("/RevisionMediaParametersInput");
        assertThat(schemas.at("/PhotoParametersInput/properties/captureTime/format").asText()).isEqualTo("date-time");
        assertThat(schemas.at("/PhotoParametersInput/properties/cameraBody/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/PhotoParametersInput/properties/lens/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/PhotoParametersInput/properties/focalLength/maxLength").asInt()).isEqualTo(50);
        assertThat(schemas.at("/FieldError/properties/path/description").asText())
                .contains("mediaParameters[2].parameters.captureTime");
        assertThat(schemas.at("/ItemError/properties/resourceId/description").asText())
                .contains("公开 mediaId");
        assertThat(schemas.at("/ItemError/properties/code/example").asText())
                .isEqualTo("INVALID_MEDIA_PARAMETERS");
        String draft400 = root.at("/paths/~1api~1v1~1photos~1{workId}~1draft/put/responses/400/content/application~1json/schema/$ref").asText();
        assertThat(root.at(draft400.substring(1) + "/properties/fieldErrors/items/$ref").asText())
                .endsWith("/FieldError");
        assertThat(root.at(draft400.substring(1) + "/properties/itemErrors/items/$ref").asText())
                .endsWith("/ItemError");
        assertThat(schemas.at("/Comment/properties/content/maxLength").asInt()).isEqualTo(1000);
        assertThat(schemas.at("/CategoryView/properties/categoryId/type").asText()).isEqualTo("string");

        JsonNode authorRevision = schemas.path("AuthorRevisionView").path("properties");
        assertThat(authorRevision.has("category")).isTrue();
        assertThat(authorRevision.at("/tags/items/$ref").asText()).endsWith("/TagView");
        assertThat(authorRevision.at("/media/items/$ref").asText()).endsWith("/RevisionMediaView");
        assertThat(authorRevision.has("mediaIds")).isFalse();
        assertThat(schemas.at("/RevisionMediaView/properties/web/$ref").asText()).endsWith("/WebMediaRef");
        assertThat(schemas.at("/RevisionMediaView/properties/parameters/$ref").asText())
                .endsWith("/PhotoParameters");
        assertThat(schemas.at("/MediaProcessingView/properties/exifCandidate/$ref").asText())
                .endsWith("/PhotoParameters");
        assertThat(schemas.at("/MediaProcessingView/properties/warnings/items/$ref").asText())
                .endsWith("/MediaWarning");
        assertThat(schemas.at("/PhotoParameters/properties/cameraBody/maxLength").asInt()).isEqualTo(100);
        assertThat(schemas.at("/PhotoParameters/properties/iso/maxLength").asInt()).isEqualTo(50);
        List<String> warningCodes = new ArrayList<>();
        schemas.at("/MediaWarning/properties/code/enum").forEach(value -> warningCodes.add(value.asText()));
        assertThat(warningCodes).containsExactly("EXIF_PARSE_FAILED", "EXIF_CAPTURE_TIME_IN_FUTURE", "EXIF_FIELD_IGNORED");
        assertThat(schemas.at("/PublicPhotoDetail/properties/media/items/$ref").asText())
                .endsWith("/RevisionMediaView");

        JsonNode moderationTarget = schemas.path("ModerationTargetView").path("properties");
        assertThat(moderationTarget.at("/targetRevision/$ref").asText()).endsWith("/AuthorRevisionView");
        assertThat(moderationTarget.at("/currentPublicRevision/$ref").asText()).endsWith("/PublicRevisionSummary");
        assertThat(moderationTarget.at("/author/$ref").asText()).endsWith("/PublicAuthorView");
        assertThat(moderationTarget.has("mediaIds")).isFalse();
    }

    private JsonNode openApi() throws Exception {
        return mapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private void assertCodes(JsonNode root, String path, String method, String status, String... expected) {
        String ref = root.path("paths").path(path).path(method).path("responses").path(status)
                .at("/content/application~1json/schema/$ref").asText();
        List<String> actual = new ArrayList<>();
        root.at(ref.substring(1) + "/properties/code/enum").forEach(value -> actual.add(value.asText()));
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    private void assertEtagRead(JsonNode root, String path) {
        JsonNode operation = root.path("paths").path(path).path("get");
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/responses/200/headers/ETag").isMissingNode()).isFalse();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("403")).isTrue();
        assertThat(operation.path("responses").has("404")).isTrue();
    }

    private void assertMutation(JsonNode root, String path, String method) {
        JsonNode operation = root.path("paths").path(path).path(method);
        List<String> parameters = new ArrayList<>();
        operation.path("parameters").forEach(value -> parameters.add(value.path("name").asText()));
        assertThat(parameters).contains("If-Match", "Idempotency-Key");
        operation.path("parameters").forEach(value -> {
            if (Set.of("If-Match", "Idempotency-Key").contains(value.path("name").asText()))
                assertThat(value.path("required").asBoolean()).isTrue();
        });
        assertThat(operation.path("responses").fieldNames())
                .toIterable().contains("400", "409", "412", "428", "500", "503");
        assertThat(operation.at("/responses/200/headers/ETag").isMissingNode()).isFalse();
    }

    private String successSchema(JsonNode root, String path, String method) {
        JsonNode content = root.path("paths").path(path).path(method).at("/responses/200/content");
        return content.elements().next().path("schema").path("$ref").asText();
    }
}
