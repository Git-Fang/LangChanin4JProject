package org.fb.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsCommandListener;
import io.micrometer.core.instrument.binder.mongodb.MongoMetricsConnectionPoolListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MongoDB监控配置类
 */
@Configuration
public class MongoMonitoringConfig {

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/chat_db}")
    private String mongoUri;

    /**
     * 配置MeterRegistry自定义器，为所有指标添加通用标签
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> mongoMeterRegistryCustomizer() {
        return registry -> {
            registry.config().commonTags("application", "RAGTranslationApplication", "component", "mongodb");
        };
    }

    /**
     * 配置MongoDB命令监听器，用于收集命令执行指标
     */
    @Bean
    public MongoMetricsCommandListener mongoMetricsCommandListener(MeterRegistry meterRegistry) {
        return new MongoMetricsCommandListener(meterRegistry);
    }

    /**
     * 配置MongoDB连接池监听器，用于收集连接池指标
     */
    @Bean
    public MongoMetricsConnectionPoolListener mongoMetricsConnectionPoolListener(MeterRegistry meterRegistry) {
        return new MongoMetricsConnectionPoolListener(meterRegistry);
    }

    /**
     * 配置MongoClient，集成监控功能
     */
    @Bean
    public MongoClient mongoClientWithMetrics(MongoMetricsCommandListener commandListener, MongoMetricsConnectionPoolListener poolListener) {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .addCommandListener(commandListener)
                .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(poolListener))
                .build();

        return MongoClients.create(settings);
    }
}