package org.fb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 术语匹配缓存配置
 * 支持术语匹配结果的缓存以提高性能
 */
@Slf4j
@Configuration
@EnableCaching
public class TermMatchCacheConfig {

    /**
     * 缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("初始化术语匹配缓存管理器");
        return new ConcurrentMapCacheManager(
            "termMatches",      // 术语匹配结果缓存
            "termMetadata",     // 术语元数据缓存
            "pinyinCache"       // 拼音转换缓存
        );
    }
}