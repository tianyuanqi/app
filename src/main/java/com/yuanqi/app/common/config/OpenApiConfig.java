package com.yuanqi.app.common.config;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;

/** OpenAPI 全局定义与 v1.0 Operation 级闭合错误契约。 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "2400px API", version = "1.0",
        description = "2400px v1.0 模块化单体 Backend；运行时 /v3/api-docs 为机器可读权威契约"))
@SecurityScheme(name = "Authorization", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer")
public class OpenApiConfig {
    private static final Set<String> ETAG_READS = Set.of("/api/v1/users/me", "/api/v1/admin/users/{uid}",
            "/api/v1/media/photos/{mediaId}", "/api/v1/photos/{workId}/author-view",
            "/api/v1/photos/{workId}/draft", "/api/v1/moderation/photos/{workId}",
            "/api/v1/moderation/photos/{workId}/revisions/{revisionId}", "/api/v1/media/{mediaId}/web");
    private static final EnumSet<ErrorCode> C = es(ErrorCode.INTERNAL_ERROR, ErrorCode.SERVICE_UNAVAILABLE);
    private static final EnumSet<ErrorCode> B = es(ErrorCode.AUTH_REQUIRED, ErrorCode.ACCESS_TOKEN_EXPIRED,
            ErrorCode.SESSION_INVALID, ErrorCode.ACCOUNT_UNAVAILABLE);
    private static final EnumSet<ErrorCode> OB = es(ErrorCode.ACCESS_TOKEN_EXPIRED, ErrorCode.SESSION_INVALID,
            ErrorCode.ACCOUNT_UNAVAILABLE);
    private static final EnumSet<ErrorCode> A = add(B, ErrorCode.FORBIDDEN);
    private static final EnumSet<ErrorCode> O = add(B, ErrorCode.RESOURCE_NOT_FOUND);
    private static final EnumSet<ErrorCode> E = es(ErrorCode.PRECONDITION_REQUIRED, ErrorCode.INVALID_IF_MATCH,
            ErrorCode.PRECONDITION_FAILED);
    private static final EnumSet<ErrorCode> I = es(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
            ErrorCode.INVALID_IDEMPOTENCY_KEY, ErrorCode.IDEMPOTENCY_KEY_REUSED, ErrorCode.IDEMPOTENCY_IN_PROGRESS);
    private static final EnumSet<ErrorCode> P = es(ErrorCode.INVALID_PAGE, ErrorCode.INVALID_PAGE_SIZE,
            ErrorCode.INVALID_SORT, ErrorCode.INVALID_FILTER, ErrorCode.INVALID_QUERY);
    private static final Map<Key, EnumSet<ErrorCode>> ERRORS = errors();

    @Bean OpenApiCustomizer v1ContractResponses() {
        return api -> {
            ModelConverters.getInstance().readAll(ErrorResult.class)
                    .forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
            Set<Key> runtime = new HashSet<>();
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                Key key = new Key(method.name(), path);
                runtime.add(key);
                EnumSet<ErrorCode> allowed = ERRORS.get(key);
                if (allowed == null) throw new IllegalStateException("OpenAPI Operation 未配置闭合错误集合: " + key);
                restricted(api.getComponents().getSchemas(), operation, key, allowed);
                requireContractHeaders(operation);
                if (ETAG_READS.contains(path) || parameter(operation, "If-Match")) etag(operation);
            }));
            Set<Key> stale = new HashSet<>(ERRORS.keySet());
            stale.removeAll(runtime);
            if (!stale.isEmpty()) throw new IllegalStateException("闭合错误注册表存在无效 Operation: " + stale);
        };
    }

    private void restricted(Map<String, Schema> schemas, Operation op, Key key, Set<ErrorCode> allowed) {
        Map<Integer, List<ErrorCode>> groups = allowed.stream().collect(Collectors.groupingBy(
                e -> e.getHttpStatus().value(), TreeMap::new, Collectors.toList()));
        groups.forEach((status, values) -> {
            values.sort(Enum::compareTo);
            String name = "ErrorResult_" + key.stem() + "_" + status;
            schemas.put(name, errorSchema(values));
            op.getResponses().addApiResponse(status.toString(), new ApiResponse()
                    .description(values.stream().map(Enum::name).collect(Collectors.joining("、")))
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + name)))));
        });
    }

    private Schema<?> errorSchema(List<ErrorCode> values) {
        return new ObjectSchema().description("该 Operation 与 HTTP 状态允许的闭合错误响应")
                .addProperty("code", new StringSchema()._enum(values.stream().map(Enum::name).toList()))
                .addProperty("message", new StringSchema()).addProperty("retryable", new BooleanSchema())
                .addProperty("retryAfterSeconds", new IntegerSchema().format("int32"))
                .addProperty("fieldErrors", new ArraySchema().items(ref("FieldError")))
                .addProperty("itemErrors", new ArraySchema().items(ref("ItemError")))
                .addProperty("conflict", ref("ConflictView")).addProperty("verification", ref("VerificationErrorContext"))
                .addProperty("traceId", new StringSchema());
    }

    private Schema<?> ref(String name) { return new Schema<>().$ref("#/components/schemas/" + name); }
    private boolean parameter(Operation op, String name) { return op.getParameters() != null && op.getParameters().stream().anyMatch(p -> name.equalsIgnoreCase(p.getName())); }
    private void requireContractHeaders(Operation op) {
        if (op.getParameters() == null) return;
        op.getParameters().stream().filter(p -> "If-Match".equalsIgnoreCase(p.getName())
                        || "Idempotency-Key".equalsIgnoreCase(p.getName()))
                .forEach(p -> p.setRequired(true));
    }
    private void etag(Operation op) { op.getResponses().entrySet().stream().filter(e -> e.getKey().startsWith("2")).forEach(e -> e.getValue().addHeaderObject("ETag", new Header().description("最新原始强 ETag").schema(new StringSchema()))); }

    private static Map<Key, EnumSet<ErrorCode>> errors() {
        Map<Key, EnumSet<ErrorCode>> m = new LinkedHashMap<>();
        op(m,"GET","/api/v1/auth/csrf",ErrorCode.SESSION_INVALID,ErrorCode.ACCOUNT_UNAVAILABLE,ErrorCode.ORIGIN_NOT_ALLOWED);
        op(m,"POST","/api/v1/auth/verification-codes",I,ErrorCode.VALIDATION_FAILED,ErrorCode.ORIGIN_NOT_ALLOWED,ErrorCode.EMAIL_ALREADY_REGISTERED,ErrorCode.RESEND_TOO_SOON,ErrorCode.RATE_LIMITED,ErrorCode.MAIL_DELIVERY_UNAVAILABLE);
        op(m,"POST","/api/v1/auth/register",ErrorCode.VALIDATION_FAILED,ErrorCode.VERIFICATION_CODE_INVALID,ErrorCode.VERIFICATION_CODE_EXPIRED,ErrorCode.ORIGIN_NOT_ALLOWED,ErrorCode.VERIFICATION_FLOW_CONSUMED,ErrorCode.EMAIL_ALREADY_REGISTERED,ErrorCode.REGISTRATION_ALREADY_COMPLETED,ErrorCode.VERIFICATION_CODE_EXHAUSTED,ErrorCode.RATE_LIMITED,ErrorCode.MAIL_DELIVERY_UNAVAILABLE);
        op(m,"POST","/api/v1/auth/login",ErrorCode.VALIDATION_FAILED,ErrorCode.INVALID_CREDENTIALS,ErrorCode.ACCOUNT_UNAVAILABLE,ErrorCode.ORIGIN_NOT_ALLOWED,ErrorCode.ACCOUNT_LOCKED,ErrorCode.RATE_LIMITED);
        op(m,"POST","/api/v1/auth/token/refresh",ErrorCode.SESSION_INVALID,ErrorCode.REFRESH_REUSED,ErrorCode.ACCOUNT_UNAVAILABLE,ErrorCode.CSRF_INVALID,ErrorCode.ORIGIN_NOT_ALLOWED);
        op(m,"POST","/api/v1/auth/logout",ErrorCode.CSRF_INVALID,ErrorCode.ORIGIN_NOT_ALLOWED);
        op(m,"GET","/api/v1/users/me",B); op(m,"PUT","/api/v1/users/me",B,E,ErrorCode.VALIDATION_FAILED);
        op(m,"POST","/api/v1/users/me/avatar",B,E,I,ErrorCode.PAYLOAD_TOO_LARGE,ErrorCode.UNSUPPORTED_MEDIA_TYPE,ErrorCode.INVALID_CONTENT,ErrorCode.UNSUPPORTED_FORMAT,ErrorCode.DYNAMIC_IMAGE,ErrorCode.FILE_TOO_LARGE,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"DELETE","/api/v1/users/me/avatar",B,E);
        op(m,"GET","/api/v1/users/{uid}",OB,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"GET","/api/v1/users/{uid}/photos",OB,ErrorCode.INVALID_PAGE,ErrorCode.INVALID_QUERY,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"GET","/api/v1/admin/users",A,P); op(m,"GET","/api/v1/admin/users/{uid}",A,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.TARGET_NOT_GOVERNABLE);
        op(m,"POST","/api/v1/admin/users/{uid}/disable",A,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT,ErrorCode.TARGET_NOT_GOVERNABLE);
        op(m,"POST","/api/v1/admin/users/{uid}/enable",A,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT,ErrorCode.TARGET_NOT_GOVERNABLE);
        op(m,"GET","/api/v1/admin/users/{uid}/governance-events",A,P,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"POST","/api/v1/media/photos",B,I,ErrorCode.VALIDATION_FAILED,ErrorCode.PAYLOAD_TOO_LARGE,ErrorCode.UNSUPPORTED_MEDIA_TYPE,ErrorCode.INVALID_CONTENT,ErrorCode.UNSUPPORTED_FORMAT,ErrorCode.DYNAMIC_IMAGE,ErrorCode.FILE_TOO_LARGE,ErrorCode.RATE_LIMITED,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"GET","/api/v1/media/photos/{mediaId}",O); op(m,"POST","/api/v1/media/photos/{mediaId}/retry",O,E,I,ErrorCode.STATE_CONFLICT,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"DELETE","/api/v1/media/photos/{mediaId}",O,E,ErrorCode.STATE_CONFLICT,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"GET","/api/v1/media/{mediaId}/web",OB,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"POST","/api/v1/photos",B,I,ErrorCode.VALIDATION_FAILED,ErrorCode.STATE_CONFLICT,ErrorCode.PROCESSING_FAILED);
        op(m,"GET","/api/v1/photos",OB,ErrorCode.INVALID_PAGE,ErrorCode.INVALID_FILTER,ErrorCode.INVALID_QUERY);
        op(m,"GET","/api/v1/photos/mine",B,P); op(m,"GET","/api/v1/photos/{workId}",OB,ErrorCode.RESOURCE_NOT_FOUND); op(m,"GET","/api/v1/photos/{workId}/author-view",O);
        op(m,"POST","/api/v1/photos/{workId}/draft",O,E,I,ErrorCode.STATE_CONFLICT); op(m,"GET","/api/v1/photos/{workId}/draft",O);
        op(m,"PUT","/api/v1/photos/{workId}/draft",O,E,ErrorCode.VALIDATION_FAILED,ErrorCode.STATE_CONFLICT,ErrorCode.PROCESSING_FAILED);
        op(m,"POST","/api/v1/photos/{workId}/submit",O,E,I,ErrorCode.STATE_CONFLICT,ErrorCode.PROCESSING_FAILED);
        op(m,"POST","/api/v1/photos/{workId}/withdraw",O,E,I,ErrorCode.STATE_CONFLICT);
        op(m,"DELETE","/api/v1/photos/{workId}",O,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.STATE_CONFLICT,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"GET","/api/v1/moderation/photos",A,P); op(m,"GET","/api/v1/moderation/photos/{workId}",A,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"GET","/api/v1/moderation/photos/{workId}/revisions/{revisionId}",A,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"GET","/api/v1/moderation/photos/{workId}/history",A,P,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"POST","/api/v1/moderation/photos/{workId}/revisions/{revisionId}/approve",A,E,I,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT);
        op(m,"POST","/api/v1/moderation/photos/{workId}/revisions/{revisionId}/reject",A,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT);
        op(m,"POST","/api/v1/moderation/photos/{workId}/offline",A,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT);
        op(m,"DELETE","/api/v1/moderation/photos/{workId}",A,E,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT,ErrorCode.STORAGE_UNAVAILABLE);
        op(m,"GET","/api/v1/categories",ErrorCode.INVALID_QUERY); op(m,"GET","/api/v1/tags",ErrorCode.VALIDATION_FAILED,ErrorCode.INVALID_QUERY);
        op(m,"PUT","/api/v1/photos/{workId}/like",B,ErrorCode.RESOURCE_NOT_FOUND); op(m,"DELETE","/api/v1/photos/{workId}/like",B,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"GET","/api/v1/photos/{workId}/comments",OB,ErrorCode.INVALID_PAGE,ErrorCode.INVALID_QUERY,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"POST","/api/v1/photos/{workId}/comments",B,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.RATE_LIMITED);
        op(m,"GET","/api/v1/photos/{workId}/comments/{rootId}/replies",OB,ErrorCode.INVALID_CURSOR,ErrorCode.INVALID_PAGE_SIZE,ErrorCode.INVALID_QUERY,ErrorCode.RESOURCE_NOT_FOUND);
        op(m,"POST","/api/v1/photos/{workId}/comments/{rootId}/replies",B,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT,ErrorCode.RATE_LIMITED);
        op(m,"DELETE","/api/v1/photos/{workId}/comments/{commentId}",B,I,ErrorCode.FORBIDDEN,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT);
        op(m,"DELETE","/api/v1/moderation/comments/{commentId}",A,I,ErrorCode.VALIDATION_FAILED,ErrorCode.RESOURCE_NOT_FOUND,ErrorCode.STATE_CONFLICT);
        m.replaceAll((k,v) -> add(v,C)); return Map.copyOf(m);
    }

    private static void op(Map<Key,EnumSet<ErrorCode>> m,String method,String path,Object... groups) {
        EnumSet<ErrorCode> r=EnumSet.noneOf(ErrorCode.class);
        for(Object g:groups) if(g instanceof ErrorCode e) r.add(e); else if(g instanceof Set<?> s) s.forEach(x->r.add((ErrorCode)x)); else throw new IllegalArgumentException();
        if(m.put(new Key(method,path),r)!=null) throw new IllegalStateException("重复 Operation: "+method+" "+path);
    }
    private static EnumSet<ErrorCode> es(ErrorCode first,ErrorCode... rest){return EnumSet.of(first,rest);}
    private static EnumSet<ErrorCode> add(Set<ErrorCode> base,ErrorCode... values){EnumSet<ErrorCode> r=EnumSet.copyOf(base);r.addAll(List.of(values));return r;}
    private static EnumSet<ErrorCode> add(Set<ErrorCode> left,Set<ErrorCode> right){EnumSet<ErrorCode> r=EnumSet.copyOf(left);r.addAll(right);return r;}
    private record Key(String method,String path){Key{method=method.toUpperCase(Locale.ROOT);}String stem(){return(method+"_"+path.replace("/api/v1/","").replaceAll("[^A-Za-z0-9]+","_")).replaceAll("_+$","");}}
}
