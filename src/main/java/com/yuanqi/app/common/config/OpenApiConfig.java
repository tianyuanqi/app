package com.yuanqi.app.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.headers.Header;

import java.util.Set;
import io.swagger.v3.core.converter.ModelConverters;
import com.yuanqi.app.common.api.ErrorResult;

/**
 * OpenAPI / Swagger 全局定义：文档信息与 Bearer JWT 方案。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "2400px API",
                version = "1.0",
                description = "2400px v1.0 模块化单体 Backend；运行时 /v3/api-docs 为机器可读权威契约"
        )
)
@SecurityScheme(
        name = "Authorization",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {
    private static final Set<String> ETAG_READS = Set.of(
            "/api/v1/users/me", "/api/v1/admin/users/{uid}",
            "/api/v1/media/photos/{mediaId}", "/api/v1/photos/{workId}/author-view",
            "/api/v1/photos/{workId}/draft", "/api/v1/moderation/photos/{workId}",
            "/api/v1/moderation/photos/{workId}/revisions/{revisionId}");

    /** 补齐所有 Operation 的统一错误 Schema，并声明条件请求与幂等错误响应。 */
    @Bean
    OpenApiCustomizer v1ContractResponses() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ErrorResult.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            error(operation, "500", "INTERNAL_ERROR");
            error(operation, "503", "SERVICE_UNAVAILABLE");
            boolean key = hasParameter(operation, "Idempotency-Key");
            boolean match = hasParameter(operation, "If-Match");
            if (key) {
                error(operation, "400", "IDEMPOTENCY_KEY_REQUIRED、INVALID_IDEMPOTENCY_KEY；以及 Operation 的 Validation 错误");
                error(operation, "409", "IDEMPOTENCY_KEY_REUSED、IDEMPOTENCY_IN_PROGRESS；以及 Operation 的状态冲突");
            }
            if (match) {
                error(operation, "400", "INVALID_IF_MATCH；以及 Operation 的 Validation 错误");
                error(operation, "412", "PRECONDITION_FAILED");
                error(operation, "428", "PRECONDITION_REQUIRED");
            }
            if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                error(operation, "401", "AUTH_REQUIRED、ACCESS_TOKEN_EXPIRED、SESSION_INVALID");
                error(operation, "403", "ACCOUNT_UNAVAILABLE、FORBIDDEN");
            }
            if (path.contains("{") && !path.startsWith("/api/v1/auth/")) {
                error(operation, "404", "RESOURCE_NOT_FOUND");
            }
            if (ETAG_READS.contains(path) || match) successHeader(operation, "ETag", "最新原始强 ETag");
            }));
        };
    }

    private boolean hasParameter(io.swagger.v3.oas.models.Operation operation, String name) {
        return operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> name.equalsIgnoreCase(parameter.getName()));
    }

    private void error(io.swagger.v3.oas.models.Operation operation, String status, String description) {
        operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResult")))));
    }

    private void successHeader(io.swagger.v3.oas.models.Operation operation, String name, String description) {
        operation.getResponses().entrySet().stream().filter(entry -> entry.getKey().startsWith("2"))
                .forEach(entry -> entry.getValue().addHeaderObject(name,
                        new Header().description(description).schema(new StringSchema())));
    }
}
