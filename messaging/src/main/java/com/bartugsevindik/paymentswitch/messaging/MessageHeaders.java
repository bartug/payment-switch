/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

/**
 * <h1>MessageHeaders</h1>
 * <p>Tüm servislerin Kafka mesajlarında kullandığı ortak header'lar.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public final class MessageHeaders {

    /**
     * Event'in tekil ID'si. Consumer'lar tekrar eden mesajları bu değerle ayıklar (inbox).
     */
    public static final String EVENT_ID = "event-id";
    public static final String EVENT_TYPE = "event-type";

    private MessageHeaders() {
    }

    public static String read(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
