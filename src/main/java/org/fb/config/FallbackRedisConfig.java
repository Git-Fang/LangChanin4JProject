package org.fb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis健康检查和降级配置
 */
@Component
public class RedisHealthIndicator {
    
    private final StringRedisTemplate redisTemplate;
    
    // Redis是否可用的状态标志
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);
    
    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 检查Redis连接是否可用
     */
    public boolean isRedisAvailable() {
        if (!redisAvailable.get()) {
            // 如果已知不可用，快速返回
            return false;
        }
        
        try {
            // 尝试ping Redis服务器
            redisTemplate.ping();
            redisAvailable.set(true);
            return true;
        } catch (Exception e) {
            redisAvailable.set(false);
            return false;
        }
    }
    
    /**
     * 标记Redis为不可用
     */
    public void markRedisUnavailable() {
        redisAvailable.set(false);
    }
    
    /**
     * 标记Redis为可用
     */
    public void markRedisAvailable() {
        redisAvailable.set(true);
    }
}