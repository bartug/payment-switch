/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.consumer;

/**
 * <h1>IncomingEvent</h1>
 * <p>Kafka'dan okunmuş ve çözülmüş event.</p>
 *
 * @param eventId Inbox kontrolünde kullanılacak event ID
 * @param payload Event
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
public record IncomingEvent<T>(String eventId, T payload) {
}
