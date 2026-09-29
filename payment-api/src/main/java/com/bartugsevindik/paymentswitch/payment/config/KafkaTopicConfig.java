/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.payment.config;

import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankOperationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import com.bartugsevindik.paymentswitch.messaging.config.MessagingAutoConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Broker'da auto-create kapalı; servisin ürettiği ve tükettiği topic'ler uygulama açılırken yoksa oluşturulur.
 * Consumer olmayan bir topic'e abone olursa topic sonradan oluşsa bile metadata yenilenene kadar mesaj almaz.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public KafkaAdmin.NewTopics paymentTopics(@Value("${application.kafka.topic-partitions:6}") int partitions,
                                             @Value("${application.kafka.topic-replicas:1}") short replicas) {
        return new KafkaAdmin.NewTopics(
                topic(PaymentRequestedEvent.TOPIC, partitions, replicas),
                topic(PaymentRoutingResultEvent.TOPIC, partitions, replicas),
                topic(PaymentRoutingResultEvent.TOPIC + MessagingAutoConfiguration.DLT_SUFFIX, partitions, replicas),
                topic(BankAuthorizationResultEvent.TOPIC, partitions, replicas),
                topic(BankAuthorizationResultEvent.TOPIC + MessagingAutoConfiguration.DLT_SUFFIX, partitions, replicas),
                topic(BankOperationResultEvent.TOPIC, partitions, replicas),
                topic(BankOperationResultEvent.TOPIC + MessagingAutoConfiguration.DLT_SUFFIX, partitions, replicas));
    }

    private static NewTopic topic(String name, int partitions, short replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
