package org.fb.config;

import io.qdrant.client.QdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 安全模式配置 - 当外部服务不可用时提供空实现
 */
@Configuration
public class SafeModeConfig {
    private static final Logger logger = LoggerFactory.getLogger(SafeModeConfig.class);

    @Value("${app.safe-mode:true}")
    private boolean safeMode;

    /**
     * 创建空Qdrant客户端（当服务不可用时）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.safe-mode", havingValue = "true", matchIfMissing = true)
    public QdrantClient safeQdrantClient() {
        logger.warn("Qdrant服务不可用，启动安全模式。RAG功能将受限制。");
        return null; // 容器将为null，需要检测null值进行处理
    }
}