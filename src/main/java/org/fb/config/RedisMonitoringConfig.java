package org.fb.config;

import io.micrometer.core.instrument.binder.cache.JCacheMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.File;

/**
 * Redis监控配置类
 */
@Configuration
public class RedisMonitoringConfig {



    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    /**
     * 配置Redis连接工厂监控
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> redisMeterRegistryCustomizer() {
        return registry -> {
            // 添加Redis相关标签
            registry.config().commonTags("redis-host", redisHost, "redis-port", String.valueOf(redisPort));
        };
    }

    /**
     * 配置RedisTemplate监控
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplateWithMetrics(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Redis监控将在RedisTemplate操作时自动记录指标
        // Micrometer会自动收集Redis相关指标
        
        return template;
    }

    /*
     * 配置系统监控指标
     * 注意：DiskSpaceMetrics和FileDescriptorMetrics已由Spring Boot自动配置，
     * 因此不需要在此处重新定义以避免Bean定义冲突
     */
}