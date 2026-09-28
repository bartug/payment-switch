/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.routing.config;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.event.PaymentRequestedEvent;
import com.bartugsevindik.paymentswitch.common.event.PaymentRoutingResultEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

/**
 * routing-service'in ürettiği ve tükettiği topic'ler. Tükettiği {@code payment.requested} de burada tanımlanır:
 * consumer olmayan bir topic'e abone olursa topic sonradan oluşsa bile metadata yenilenene kadar
 * ({@code metadata.max.age.ms}, 5 dk) mesaj almaz. Topic zaten varsa tanım bir şey yapmaz.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public KafkaAdmin.NewTopics routingTopics(@Value("${application.kafka.topic-partitions:6}") int partitions,
                                              @Value("${application.kafka.topic-replicas:1}") short replicas) {
        List<NewTopic> topics = new ArrayList<>();
        for (BankCode bank : BankCode.values()) {
            topics.add(TopicBuilder.name(bank.requestTopic()).partitions(partitions).replicas(replicas).build());
        }
        topics.add(TopicBuilder.name(PaymentRequestedEvent.TOPIC).partitions(partitions).replicas(replicas).build());
        topics.add(TopicBuilder.name(PaymentRoutingResultEvent.TOPIC).partitions(partitions).replicas(replicas).build());
        topics.add(TopicBuilder.name(PaymentRequestedEvent.TOPIC + ".DLT").partitions(partitions).replicas(replicas).build());
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
