package org.fb.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers:}")
    private String bootstrapServers;
    
    private boolean kafkaAvailable = false;
    
    @PostConstruct
    public void checkKafkaAvailability() {
        if (bootstrapServers != null && !bootstrapServers.isEmpty()) {
            try {
                java.net.Socket socket = new java.net.Socket();
                String host = bootstrapServers.split(":")[0];
                int port = Integer.parseInt(bootstrapServers.split(":")[1]);
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                socket.close();
                kafkaAvailable = true;
                System.out.println("[KafkaConfig] Kafka连接成功: " + bootstrapServers);
            } catch (Exception e) {
                System.out.println("[KafkaConfig] Kafka不可用，将使用同步处理模式: " + e.getMessage());
                kafkaAvailable = false;
            }
        }
    }
    
    public boolean isKafkaAvailable() {
        return kafkaAvailable;
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic aiRequestTopic() {
        return TopicBuilder.name("ai-chat-request")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic aiResultTopic() {
        return TopicBuilder.name("ai-chat-result")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "86400000")
                .build();
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public NewTopic aiDlqTopic() {
        return TopicBuilder.name("ai-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public ProducerFactory<String, ChatRequestMessage> chatRequestProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public KafkaTemplate<String, ChatRequestMessage> chatRequestKafkaTemplate() {
        return new KafkaTemplate<>(chatRequestProducerFactory());
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public ProducerFactory<String, ChatResultMessage> chatResultProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public KafkaTemplate<String, ChatResultMessage> chatResultKafkaTemplate() {
        return new KafkaTemplate<>(chatResultProducerFactory());
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public ConsumerFactory<String, ChatRequestMessage> chatRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-request-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatRequestMessage.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> 
            chatRequestListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(chatRequestConsumerFactory());
        factory.setConcurrency(2);
        factory.setBatchListener(false);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
    
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }
}
