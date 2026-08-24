package com.yuanqi.app.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

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
        assertThat(operation.path("responses").fieldNames())
                .toIterable().contains("400", "409", "412", "428", "500", "503");
        assertThat(operation.at("/responses/200/headers/ETag").isMissingNode()).isFalse();
    }

    private String successSchema(JsonNode root, String path, String method) {
        JsonNode content = root.path("paths").path(path).path(method).at("/responses/200/content");
        return content.elements().next().path("schema").path("$ref").asText();
    }
}
