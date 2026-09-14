package com.moveiq.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> template,
            @Value("${moveiq.kafka.dlt-suffix:.DLT}") String dltSuffix) {
        var recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + dltSuffix, record.partition()));
        var backoff = new ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(500L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(8_000L);
        return new DefaultErrorHandler(recoverer, backoff);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(3);
        return factory;
    }
}
