/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency;

import com.bartugsevindik.paymentswitch.payment.idempotency.config.IdempotencyProperties;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.IdempotencyLock;
import com.bartugsevindik.paymentswitch.payment.idempotency.lock.LockResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyLockTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final IdempotencyLock lock = new IdempotencyLock(redisTemplate, properties());

    @Test
    void redisCokerseKilitAlinmisSayilir() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(lock.tryAcquire("idem:lock:MRC:key", "token")).isEqualTo(LockResult.SKIPPED);
    }

    @Test
    void kilitBaskasindaysaFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idem:lock:MRC:key"), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(lock.tryAcquire("idem:lock:MRC:key", "token")).isEqualTo(LockResult.HELD_BY_OTHER);
    }

    @Test
    void redisCokerseReleaseHataFirlatmaz() {
        when(redisTemplate.execute(any(), anyList(), any())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> lock.release("idem:lock:MRC:key", "token")).doesNotThrowAnyException();
    }

    private static IdempotencyProperties properties() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setHashSecret("test");
        return properties;
    }
}
