package org.fb.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.DnsResolver;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import io.lettuce.core.api.StatefulConnection;

@Configuration
public class RedisConfig {

    @Value("${SPRING_REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${SPRING_REDIS_PORT:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        return DefaultClientResources.create();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        
        // 配置连接池
        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(100);                 // 调整最大连接数
        poolConfig.setMaxIdle(20);                  // 调整最大空闲连接数
        poolConfig.setMinIdle(5);                   // 调整最小空闲连接数
        poolConfig.setMaxWaitMillis(5000);          // 减少最大等待时间
        poolConfig.setTestOnBorrow(true);           // 借用连接时检测
        poolConfig.setTestOnReturn(false);          // 归还连接时检测
        poolConfig.setTestWhileIdle(true);          // 空闲时检测
        poolConfig.setTimeBetweenEvictionRunsMillis(30000); // 空闲连接检测周期
        poolConfig.setMinEvictableIdleTimeMillis(60000);    // 最小空闲时间
                
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .clientResources(clientResources)
            .poolConfig(poolConfig)
            .commandTimeout(Duration.ofSeconds(10))  // 命令超时时间
            .shutdownTimeout(Duration.ofSeconds(2))  // 关闭超时时间
            .build();
        
        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}