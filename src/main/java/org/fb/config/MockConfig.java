package org.fb.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Mock配置：Docker化时用来屏蔽AI模型加载失败的配置。
 * 通过跳过AI相关Bean的初始化，让项目在没有有效API密钥时也能启动。
 */
@Configuration
@Profile("docker-bypass")
public class MockConfig {

    /**
     * 创建一个Mock版本的Embedding模型，用于禁用AI相关功能
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "feature.mock.skip-embedding", havingValue = "true", matchIfMissing = true)
    public EmbeddingModel mockEmbeddingModel() {
        return new MockEmbeddingModel();
    }

    /**
     * Mock的EmbeddingModel实现
     */
    private static class MockEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<dev.langchain4j.data.embedding.Embedding> embed(String text) {
            // 返回长度为384的零向量作为Mock结果
            return Response.from(dev.langchain4j.data.embedding.Embedding.from(new float[384]));
        }

        @Override
        public Response<java.util.List<dev.langchain4j.data.embedding.Embedding>> embedAll(java.util.List<TextSegment> textSegments) {
            java.util.List<dev.langchain4j.data.embedding.Embedding> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < textSegments.size(); i++) {
                embeddings.add(dev.langchain4j.data.embedding.Embedding.from(new float[384]));
            }
            return Response.from(embeddings);
        }
    }
}