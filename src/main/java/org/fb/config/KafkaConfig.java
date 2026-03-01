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
import org.springframework.context.annotation.Profile;
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
@Profile("!standalone")
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.consumer.enabled:true}")
    private boolean consumerEnabled;
    
    private boolean kafkaAvailable = false;
    
    @PostConstruct
    public void checkKafkaAvailability() {
        if (bootstrapServers != null && !bootstrapServers.isEmpty()) {
            try {
                String host = bootstrapServers.split(":")[0];
                int port = Integer.parseInt(bootstrapServers.split(":")[1]);
                
                // 使用更可靠的方式检查Kafka可用性
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                socket.close();
                kafkaAvailable = true;
                System.out.println("[KafkaConfig] Kafka连接成功: " + bootstrapServers);
                
                // 额外验证：检查Zookeeper连接
                String zkHost = "zookeeper";
                int zkPort = 2181;
                java.net.Socket zkSocket = new java.net.Socket();
                zkSocket.connect(new java.net.InetSocketAddress(zkHost, zkPort), 5000);
                zkSocket.close();
                System.out.println("[KafkaConfig] Zookeeper连接成功: " + zkHost + ":" + zkPort);
                
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
    public NewTopic aiRequestTopic() {
        return TopicBuilder.name("ai-chat-request")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }
    
    @Bean
    public NewTopic aiResultTopic() {
        return TopicBuilder.name("ai-chat-result")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "86400000")
                .build();
    }
    
    @Bean
    public NewTopic aiDlqTopic() {
        return TopicBuilder.name("ai-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }
    
    @Bean
    @Primary
    public ProducerFactory<String, ChatRequestMessage> chatRequestProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 5);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    @Primary
    public KafkaTemplate<String, ChatRequestMessage> chatRequestKafkaTemplate() {
        return new KafkaTemplate<>(chatRequestProducerFactory());
    }
    
    @Bean
    @Primary
    public ProducerFactory<String, ChatResultMessage> chatResultProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 5);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    @Primary
    public KafkaTemplate<String, ChatResultMessage> chatResultKafkaTemplate() {
        return new KafkaTemplate<>(chatResultProducerFactory());
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
    public ConsumerFactory<String, ChatRequestMessage> chatRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-request-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 60000);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 5000);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 5000);
        props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 30000);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatRequestMessage.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    @Primary
    @ConditionalOnProperty(name = "kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
    public ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> 
            chatRequestListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(chatRequestConsumerFactory());
        factory.setConcurrency(2);
        factory.setBatchListener(false);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(10000);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
            new org.springframework.util.backoff.FixedBackOff(1000L, 3L)));
        return factory;
    }
}
