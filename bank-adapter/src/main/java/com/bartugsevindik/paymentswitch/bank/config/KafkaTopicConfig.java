/*
 * Copyright (c) 2026. Bartuğ Sevindik <bartugsevindik@gmail.com>
 *
 */

package com.bartugsevindik.paymentswitch.bank.config;

import com.bartugsevindik.paymentswitch.common.enums.BankCode;
import com.bartugsevindik.paymentswitch.common.event.BankAuthorizationResultEvent;
import com.bartugsevindik.paymentswitch.common.event.BankHealthChangedEvent;
import com.bartugsevindik.paymentswitch.messaging.config.MessagingAutoConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

/**
 * Tüketilen banka topic'leri (ve DLT'leri) ile üretilen sonuç ve sağlık topic'leri.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public KafkaAdmin.NewTopics bankAdapterTopics(BankAdapterProperties properties,
                                                  @Value("${application.kafka.topic-partitions:6}") int partitions,
                                                  @Value("${application.kafka.topic-replicas:1}") short replicas) {
        List<NewTopic> topics = new ArrayList<>();
        for (BankCode bank : properties.getBanks()) {
            topics.add(TopicBuilder.name(bank.requestTopic()).partitions(partitions).replicas(replicas).build());
            topics.add(TopicBuilder.name(bank.requestTopic() + MessagingAutoConfiguration.DLT_SUFFIX)
                    .partitions(partitions).replicas(replicas).build());
        }
        topics.add(TopicBuilder.name(BankAuthorizationResultEvent.TOPIC).partitions(partitions).replicas(replicas).build());
        topics.add(TopicBuilder.name(BankHealthChangedEvent.TOPIC).partitions(1).replicas(replicas).build());
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
