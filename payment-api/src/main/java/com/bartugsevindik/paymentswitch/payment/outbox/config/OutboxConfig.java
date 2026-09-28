/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.outbox.config;

import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.payment.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
@EnableJpaRepositories(basePackages = {"com.bartugsevindik.paymentswitch.payment.outbox.repository"})
public class OutboxConfig {

    // Broker'da auto-create kapalı; topic uygulama açılırken yoksa oluşturulur
    @Bean
    public NewTopic paymentRequestedTopic(OutboxProperties properties) {
        return TopicBuilder.name(PaymentRequestedEvent.TOPIC)
                .partitions(properties.getTopicPartitions())
                .replicas(properties.getTopicReplicas())
                .build();
    }

    /**
     * {@code outbox_pending_events}: gönderilmeyi bekleyen event sayısı.
     * {@code outbox_oldest_pending_seconds}: en eski bekleyen event'in yaşı. Alarm bu metriğe kurulmalı;
     * değer büyüyorsa ödemeler bankaya gitmiyor demektir.
     */
    @Bean
    public MeterBinder outboxMetrics(OutboxEventRepository repository) {
        return registry -> {
            Gauge.builder("outbox.pending.events", repository, OutboxEventRepository::countPending)
                    .description("Kafka'ya gönderilmeyi bekleyen event sayısı")
                    .register(registry);
            Gauge.builder("outbox.oldest.pending.seconds", repository, repo -> repo.findOldestPendingCreatedDate()
                            .map(oldest -> (double) Duration.between(oldest, LocalDateTime.now()).toSeconds())
                            .orElse(0d))
                    .description("En eski bekleyen event'in yaşı (saniye)")
                    .register(registry);
        };
    }
}
