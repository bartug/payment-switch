/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.inbox;

import com.bartugsevindik.paymentswitch.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * <h1>ProcessedEvent</h1>
 * <p>Bir consumer'ın işlediği event. {@code (event_id, consumer)} unique olduğu için aynı mesaj ikinci kez
 * işlenmez. Kafka at-least-once teslim ettiği için tekrar eden mesaj normal bir durumdur.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Entity
@Table(name = "processed_event")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ProcessedEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "consumer", nullable = false, length = 64)
    private String consumer;
}
