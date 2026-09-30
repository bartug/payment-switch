/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

import java.util.Optional;

/**
 * <h1>TraceParent</h1>
 * <p>W3C trace context formatı: {@code 00-<32 hex traceId>-<16 hex spanId>-<01 örneklendi / 00 örneklenmedi>}.</p>
 * <p>Outbox event'i HTTP isteğinin thread'inde yazılır ama relay tarafından başka bir thread'de, sonradan gönderilir;
 * o anda trace context yoktur. Yazma anındaki context satıra kaydedilir, gönderirken geri yüklenir. Böylece tek bir
 * ödeme dört servis boyunca tek bir trace olarak izlenebilir.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 29.09.2026 - PS-8
 */
public final class TraceParent {

    private TraceParent() {
    }

    public static String capture(Tracer tracer) {
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        return "00-" + context.traceId() + "-" + context.spanId() + "-" + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    public static Optional<TraceContext> restore(Tracer tracer, String traceParent) {
        if (tracer == null || traceParent == null) {
            return Optional.empty();
        }
        String[] parts = traceParent.split("-");
        if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
            return Optional.empty();
        }
        return Optional.of(tracer.traceContextBuilder()
                .traceId(parts[1])
                .spanId(parts[2])
                .sampled("01".equals(parts[3]))
                .build());
    }
}
