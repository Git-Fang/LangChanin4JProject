package org.fb.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RedisTemplate包装类，提供连接失败时的容错处理
 */
@Slf4j
@Component
public class RedisTemplateWrapper {
    
    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);
    
    public RedisTemplateWrapper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 安全地设置Redis值
     */
    public boolean safeSet(String key, String value, Duration timeout) {
        if (!isRedisAvailable()) {
            log.warn("Redis不可用，跳过设置键值对: {}", key);
            return false;
        }
        
        try {
            redisTemplate.opsForValue().set(key, value, timeout);
            return true;
        } catch (Exception e) {
            log.error("Redis操作失败，标记为不可用，key: {}", key, e);
            redisAvailable.set(false);
            return false;
        }
    }
    
    /**
     * 安全地获取Redis值
     */
    public String safeGet(String key) {
        if (!isRedisAvailable()) {
            log.warn("Redis不可用，跳过获取键值: {}", key);
            return null;
        }
        
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value;
        } catch (Exception e) {
            log.error("Redis操作失败，标记为不可用，key: {}", key, e);
            redisAvailable.set(false);
            return null;
        }
    }
    
    /**
     * 安全地删除Redis键
     */
    public boolean safeDelete(String key) {
        if (!isRedisAvailable()) {
            log.warn("Redis不可用，跳过删除键: {}", key);
            return false;
        }
        
        try {
            Boolean result = redisTemplate.delete(key);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis删除操作失败，标记为不可用，key: {}", key, e);
            redisAvailable.set(false);
            return false;
        }
    }
    
    /**
     * 检查Redis是否可用，如果之前不可用，尝试重新连接
     */
    private boolean isRedisAvailable() {
        if (redisAvailable.get()) {
            return true;
        }
        
        // 尝试ping Redis服务器以检查是否已恢复
        try {
            redisTemplate.ping();
            redisAvailable.set(true);
            log.info("Redis连接已恢复");
            return true;
        } catch (Exception e) {
            log.debug("Redis仍然不可用: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 手动标记Redis为可用状态
     */
    public void markRedisAvailable() {
        redisAvailable.set(true);
    }
    
    /**
     * 手动标记Redis为不可用状态
     */
    public void markRedisUnavailable() {
        redisAvailable.set(false);
    }
    
    /**
     * 获取Redis是否可用的状态
     */
    public boolean getRedisStatus() {
        return redisAvailable.get();
    }
}