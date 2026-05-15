package com.maru.journalistbot.summarizer.config;

import com.maru.journalistbot.common.event.ArticleFetchedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer + Producer config cho summarizer-service.
 *
 * Consumer:
 *   - groupId = "summarizer-group" → scale nhiều pod, Kafka tự phân partition
 *   - MANUAL_IMMEDIATE ACK → chỉ commit offset sau khi publish news.summarized thành công
 *   - concurrency = 3 → xử lý song song 3 partition cùng lúc (1 thread/partition)
 *   - trustedPackages → chỉ deserialize class từ common module (security)
 *
 * Producer:
 *   - Dùng Object value type → publish cả ArticleSummarizedEvent và ArticleFailedEvent
 *   - acks=all + idempotent → không mất, không duplicate
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Consumer ──────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, ArticleFetchedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "summarizer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Không auto commit — dùng manual ACK
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Chỉ trust class từ common module — bảo mật deserialization
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.maru.journalistbot.common.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ArticleFetchedEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(),
                new JsonDeserializer<>(ArticleFetchedEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArticleFetchedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ArticleFetchedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // MANUAL_IMMEDIATE: ACK ngay khi gọi ack.acknowledge(), không đợi poll tiếp theo
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // 3 concurrent threads — xử lý tối đa 3 partition song song
        factory.setConcurrency(3);
        return factory;
    }

    // ── Producer ──────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        // Thêm type info vào header để consumer biết deserialize class nào
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
