package com.yuanqi.app.common.idempotency;

import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTest {
    @Autowired IdempotencyService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void 清理本测试记录() {
        jdbc.update("DELETE FROM idempotency_record");
    }

    @Test void 同载荷重放首次状态Body和ETag且不再次执行() {
        AtomicInteger calls = new AtomicInteger();
        String key = key();
        var first = execute(key, Map.of("value", 1), calls);
        var replay = execute(key, Map.of("value", 1), calls);
        assertThat(calls).hasValue(1);
        assertThat(replay.getStatusCode().value()).isEqualTo(202);
        assertThat(replay.getHeaders().getETag()).isEqualTo("\"work-1\"");
        assertThat(replay.getBody()).isEqualTo(first.getBody());
    }

    @Test void 同Key异载荷返回冲突() {
        AtomicInteger calls = new AtomicInteger();
        String key = key();
        execute(key, Map.of("value", 1), calls);
        BusinessException error = catchThrowableOfType(
                () -> execute(key, Map.of("value", 2), calls), BusinessException.class);
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(calls).hasValue(1);
    }

    @Test void 并发同载荷只执行一次并重放结果() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String key = key();
        Callable<ResponseEntity<Result<TestView>>> call = () -> execute(key, Map.of("value", 3), calls);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(call);
            var b = pool.submit(call);
            assertThat(a.get().getBody()).isEqualTo(b.get().getBody());
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls).hasValue(1);
    }

    private ResponseEntity<Result<TestView>> execute(String key, Object request, AtomicInteger calls) {
        return service.execute("uid:test", "POST", "/test", key, request, TestView.class, () -> {
            calls.incrementAndGet();
            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return ResponseEntity.accepted().header(HttpHeaders.ETAG, "\"work-1\"")
                    .body(Result.success(new TestView("stable")));
        });
    }

    private String key() { return UUID.randomUUID().toString(); }
    public record TestView(String value) {}
}
