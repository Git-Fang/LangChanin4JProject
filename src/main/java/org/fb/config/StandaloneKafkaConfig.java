package org.fb.config;

import lombok.extern.slf4j.Slf4j;
import org.fb.service.kafka.StandaloneChatRequestProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("standalone")
public class StandaloneKafkaConfig {

    @Bean
    public StandaloneChatRequestProducer chatRequestProducer() {
        log.info("[Standalone模式] 使用StandaloneChatRequestProducer");
        return new StandaloneChatRequestProducer();
    }

    @Bean
    public org.fb.service.kafka.ChatRequestConsumer chatRequestConsumer(
            StandaloneChatRequestProducer requestProducer) {
        log.info("[Standalone模式] Kafka消费者已禁用");
        return null;
    }
}
