package com.moveiq.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    @Bean
    NewTopic mobilityTopic(@Value("${moveiq.kafka.mobility-topic}") String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic mobilityDltTopic(
            @Value("${moveiq.kafka.mobility-topic}") String name,
            @Value("${moveiq.kafka.dlt-suffix:.DLT}") String suffix) {
        return TopicBuilder.name(name + suffix).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic situationTopic(@Value("${moveiq.kafka.situation-topic}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
