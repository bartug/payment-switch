/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.messaging.config;

import com.bartugsevindik.paymentswitch.messaging.consumer.EventReader;
import com.bartugsevindik.paymentswitch.messaging.consumer.InvalidEventException;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxService;
import com.bartugsevindik.paymentswitch.messaging.inbox.InboxServiceImpl;
import com.bartugsevindik.paymentswitch.messaging.inbox.ProcessedEventRepository;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxCleanupJob;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxEventRepository;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxProperties;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxRelay;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxService;
import com.bartugsevindik.paymentswitch.messaging.outbox.OutboxServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * <h1>MessagingAutoConfiguration</h1>
 * <p>Bu modülü bağımlılık olarak ekleyen her servise outbox (relay, cleanup, metrikler) ve inbox gelir.
 * Servisin yapması gereken tek şey {@code outbox_event} ve {@code processed_event} tablolarını kendi migration'larına eklemek.</p>
 * <p>Sınıflar {@code @Component} değil, bean'ler burada tanımlanır. Servisler kök paketi taradığı için
 * aksi halde bu modülü kullanmayan servislerde de bean oluşmaya çalışırdı.</p>
 *
 * @author Bartuğ Sevindik <bartugsevindik@gmail.com>
 * @since 28.09.2026 - PS-4
 */
@Slf4j
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@AutoConfigurationPackage(basePackages = "com.bartugsevindik.paymentswitch.messaging")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableJpaRepositories(basePackageClasses = {OutboxEventRepository.class, ProcessedEventRepository.class})
public class MessagingAutoConfiguration {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public OutboxService outboxService(OutboxEventRepository repository, OutboxProperties properties, ObjectMapper objectMapper) {
        return new OutboxServiceImpl(repository, properties, objectMapper);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                                   TransactionTemplate transactionTemplate, OutboxProperties properties) {
        return new OutboxRelay(repository, kafkaTemplate, transactionTemplate, properties);
    }

    @Bean
    public OutboxCleanupJob outboxCleanupJob(OutboxService outboxService) {
        return new OutboxCleanupJob(outboxService);
    }

    @Bean
    public InboxService inboxService(ProcessedEventRepository repository) {
        return new InboxServiceImpl(repository);
    }

    @Bean
    public EventReader eventReader(ObjectMapper objectMapper) {
        return new EventReader(objectMapper);
    }

    /**
     * Consumer'da hata olursa mesaj 1 sn, 2 sn, 4 sn arayla tekrar denenir, hâlâ başarısızsa {@code <topic>.DLT}'ye gönderilir
     * ve partition kalan mesajlarla devam eder. Bozuk mesaj ({@link InvalidEventException}) retry edilmez.
     * Boot bu bean'i otomatik olarak listener container'lara bağlar.
     * <p>DLT topic'lerini consumer servis oluşturmalıdır; mesaj aynı partition numarasına yazıldığı için
     * DLT'nin partition sayısı kaynak topic'ten az olmamalıdır.</p>
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2);
        backOff.setMaxAttempts(3);
        // Spring Kafka 3.3 varsayılan DLT adını "-dlt" yaptı; topic adı servisler arasında sabit kalsın diye açıkça veriliyor
        DeadLetterPublishingRecoverer dltPublisher = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Message sent to DLT. topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), ex);
            dltPublisher.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    /**
     * {@code outbox_pending_events}: gönderilmeyi bekleyen event sayısı.
     * {@code outbox_oldest_pending_seconds}: en eski bekleyen event'in yaşı. Alarm bu metriğe kurulmalı;
     * değer büyüyorsa event'ler Kafka'ya gitmiyor demektir.
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
