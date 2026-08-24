package com.yuanqi.app.common.idempotency;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 在业务事务内持久化并重放不含凭据的成功 Mutation 响应。 */
@Service
public class IdempotencyService {
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._~-]{16,64}$");
    private final JdbcTemplate jdbc;
    private final ObjectMapper canonicalMapper;
    private final ObjectMapper responseMapper;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.responseMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    @Transactional
    public <T> ResponseEntity<Result<T>> execute(String subject, String method, String canonicalPath,
                                                  String key, Object request, Class<T> dataType,
                                                  Supplier<ResponseEntity<Result<T>>> action) {
        return execute(subject, method, canonicalPath, key, request, dataType, Duration.ofHours(24), action);
    }

    @Transactional
    public <T> ResponseEntity<Result<T>> execute(String subject, String method, String canonicalPath,
                                                  String key, Object request, Class<T> dataType, Duration ttl,
                                                  Supplier<ResponseEntity<Result<T>>> action) {
        validate(key);
        String scope = sha256(subject + "\n" + method + "\n" + canonicalPath + "\n" + key);
        String requestHash = requestHash(subject, method, canonicalPath, request);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Stored stored = null;
        try {
            jdbc.execute("SET SESSION innodb_lock_wait_timeout=10");
            jdbc.update("INSERT INTO idempotency_record(scope_key,request_hash,status,created_at,expires_at) VALUES(?,?,'PROCESSING',?,?)",
                    scope, requestHash, now, now.plus(ttl));
        } catch (PessimisticLockingFailureException timeout) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    ErrorCode.IDEMPOTENCY_IN_PROGRESS.getMessage(), true, 1);
        } catch (DuplicateKeyException ignored) {
            stored = findForUpdate(scope);
            if (stored != null && !stored.expiresAt().isAfter(now)) {
                jdbc.update("DELETE FROM idempotency_record WHERE scope_key=?", scope);
                jdbc.update("INSERT INTO idempotency_record(scope_key,request_hash,status,created_at,expires_at) VALUES(?,?,'PROCESSING',?,?)",
                        scope, requestHash, now, now.plus(ttl));
                stored = null;
            }
        }
        if (stored != null) {
            if (!stored.requestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if ("COMPLETED".equals(stored.status())) {
                return replay(stored, dataType);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    ErrorCode.IDEMPOTENCY_IN_PROGRESS.getMessage(), true, 1);
        }

        ResponseEntity<Result<T>> response = action.get();
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("幂等执行器只接受带响应体的 2xx 结果");
        }
        try {
            String body = responseMapper.writeValueAsString(response.getBody());
            jdbc.update("UPDATE idempotency_record SET status='COMPLETED',http_status=?,response_body=?,response_etag=?,response_location=? WHERE scope_key=? AND request_hash=?",
                    response.getStatusCode().value(), body, response.getHeaders().getETag(),
                    response.getHeaders().getFirst(HttpHeaders.LOCATION), scope, requestHash);
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("无法持久化幂等响应", e);
        }
    }

    private Stored findForUpdate(String scope) {
        var rows = jdbc.query("SELECT request_hash,status,http_status,response_body,response_etag,response_location,expires_at FROM idempotency_record WHERE scope_key=? FOR UPDATE",
                (rs, n) -> new Stored(rs.getString(1), rs.getString(2), (Integer) rs.getObject(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),rs.getTimestamp(7).toLocalDateTime()), scope);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private <T> ResponseEntity<Result<T>> replay(Stored stored, Class<T> dataType) {
        try {
            JavaType type = responseMapper.getTypeFactory().constructParametricType(Result.class, dataType);
            Result<T> body = responseMapper.readValue(stored.body(), type);
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(stored.httpStatus());
            if (stored.etag() != null) builder.header(HttpHeaders.ETAG, stored.etag());
            if (stored.location() != null) builder.header(HttpHeaders.LOCATION, stored.location());
            return builder.body(body);
        } catch (Exception e) {
            throw new IllegalStateException("无法重放幂等响应", e);
        }
    }

    private String requestHash(String subject, String method, String path, Object request) {
        try {
            JsonNode node = canonicalMapper.valueToTree(request);
            return sha256(subject + "\n" + method + "\n" + path + "\n" + canonicalMapper.writeValueAsString(node));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法计算请求摘要", e);
        }
    }

    public static void validate(String key) {
        if (key == null || key.isBlank()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        if (!KEY.matcher(key).matches()) throw new BusinessException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Stored(String requestHash, String status, Integer httpStatus, String body,
                          String etag, String location, LocalDateTime expiresAt) {
    }
}
