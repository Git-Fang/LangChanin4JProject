package org.fb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis降级配置
 */
@Configuration
public class FallbackRedisConfig {
    
    private final StringRedisTemplate redisTemplate;
    
    // Redis是否可用的状态标志
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);
    
    public FallbackRedisConfig(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 获取Redis是否可用的状态（这里仅作为示例，实际应使用RedisHealthIndicator）
     */
    public boolean getRedisStatus(StringRedisTemplate redisTemplate) {
        try {
            // 尝试简单的Redis操作
            redisTemplate.opsForValue().get("test-key");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}