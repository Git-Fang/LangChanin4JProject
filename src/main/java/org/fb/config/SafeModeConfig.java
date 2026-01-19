package org.fb.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.qdrant.client.QdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Collections;

/**
 * 安全模式配置 - 当外部服务不可用时提供空实现
 */
@Configuration
@Profile("standalone")
public class SafeModeConfig {
    private static final Logger logger = LoggerFactory.getLogger(SafeModeConfig.class);

    @Value("${app.safe-mode:true}")
    private boolean safeMode;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.safe-mode", havingValue = "true", matchIfMissing = true)
    public QdrantClient safeQdrantClient() {
        logger.warn("Qdrant服务不可用，启动安全模式。RAG功能将受限制。");
        return null;
    }

    @Bean
    public ContentRetriever standaloneContentRetriever() {
        logger.warn("Standalone模式：使用空ContentRetriever。RAG功能将不可用。");
        return new ContentRetriever() {
            @Override
            public java.util.List<dev.langchain4j.rag.content.Content> retrieve(Query query) {
                return Collections.emptyList();
            }
        };
    }
}