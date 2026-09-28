/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.idempotency.lock;

import com.bartugsevindik.paymentswitch.payment.idempotency.config.IdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h1>IdempotencyLock</h1>
 * <p>Aynı key ile eş zamanlı gelen isteklerden sadece birinin işlenmesini sağlayan Redis kilidi ({@code SET NX PX}).</p>
 * <p>Kilit bir <b>optimizasyondur</b>, doğruluk garantisi DB unique constraint'indedir. Kilit olmasa ikinci istek,
 * ilk transaction commit olana kadar DB'de bekler ve bir connection'ı boşuna tutar. Retry fırtınasında bu pool'u tüketir.</p>
 * <p>Redis'e ulaşılamazsa <b>fail-open</b> davranır: kilit alınmış sayılır, DB constraint devreye girer.
 * Redis çöktü diye ödeme almayı durdurmuyoruz.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyLock {

    // Sadece kilidi alan token silebilir. TTL dolup başkası kilidi aldıysa onun kilidi silinmez.
    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;

    /**
     * <h1>Kilit Alma</h1>
     * <p>Key için kilit almayı dener. Redis'e ulaşılamazsa {@link LockResult#SKIPPED} döner (fail-open).</p>
     *
     * @param lockKey Redis key
     * @param token   Kilidin sahibini belirten rastgele değer
     * @return Kilit sonucu
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    public LockResult tryAcquire(@NotNull String lockKey, @NotNull String token) {
        try {
            boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, properties.getLockTtl()));
            return acquired ? LockResult.ACQUIRED : LockResult.HELD_BY_OTHER;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, idempotency lock skipped (fail-open). key={}, cause={}", lockKey, e.getMessage());
            return LockResult.SKIPPED;
        }
    }

    /**
     * <h1>Kilit Bırakma</h1>
     * <p>Kilit sadece aynı token ile bırakılabilir. Hata olursa kilit TTL ile kendiliğinden düşer.</p>
     *
     * @param lockKey Redis key
     * @param token   Kilit alınırken kullanılan değer
     * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
     * @since 28.09.2026 - PS-2
     */
    public void release(@NotNull String lockKey, @NotNull String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), token);
        } catch (DataAccessException e) {
            log.warn("Idempotency lock release failed, will expire by TTL. key={}", lockKey, e);
        }
    }
}
