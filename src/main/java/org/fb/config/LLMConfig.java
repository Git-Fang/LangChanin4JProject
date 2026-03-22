package org.fb.config;

import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM配置类
 * 配置各种AI模型和向量数据库
 */
@Configuration
public class LLMConfig {
    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(300);

    @Value("${ai.dashscope.apiKey:sk-ca175397022a41a9926ffe30b1faac81}")
    private String dashscopeApiKey;

    @Value("${ai.dashscope.model:qwen-max}")
    private String dashscopeModel;

    @Value("${ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String dashscopeUrl;

    @Value("${ai.dashscope.max-retries:3}")
    private int dashscopeMaxRetries;

    @Value("${ai.deepSeek.apiKey:}")
    private String deepSeekApiKey;

    @Value("${ai.deepSeek.model:deepseek-chat}")
    private String deepSeekModel;

    @Value("${ai.deepSeek.base-url:https://api.deepseek.com/v1}")
    private String deepSeekUrl;

    @Value("${ai.deepSeek.max-retries:3}")
    private int deepSeekMaxRetries;

    @Value("${ai.embeddingStore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port:6334}")
    private Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName:doc-summarizer}")
    private String collectionName;

    private volatile ChatModel qwenChatModel;
    private volatile ChatModel qwenVisionChatModel;
    private volatile StreamingChatModel streamingChatModel;

    public LLMConfig() {
        init();
    }

    private void init() {
        refreshQwenChatModel();
        refreshQwenVisionChatModel();
        refreshStreamingChatModel();
        log.info("LLM models initialized");
    }

    private void refreshQwenChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            log.warn("DashScope API Key未配置，Qwen模型不可用");
            this.qwenChatModel = null;
        } else {
            this.qwenChatModel = OpenAiChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .baseUrl(dashscopeUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(READ_TIMEOUT)
                    .maxRetries(dashscopeMaxRetries)
                    .build();
            log.info("Qwen Chat模型初始化成功: {}", dashscopeModel);
        }
    }

    private void refreshQwenVisionChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            log.warn("DashScope API Key未配置，Qwen Vision模型不可用");
            this.qwenVisionChatModel = null;
        } else {
            this.qwenVisionChatModel = OpenAiChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName("qwen-vl-max")
                    .baseUrl(dashscopeUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(READ_TIMEOUT)
                    .maxRetries(dashscopeMaxRetries)
                    .build();
            log.info("Qwen Vision模型(qwen-vl-max)初始化成功");
        }
    }

    private void refreshStreamingChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            log.warn("DashScope API Key未配置，Streaming模型不可用");
            this.streamingChatModel = null;
        } else {
            this.streamingChatModel = QwenStreamingChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .build();
            log.info("Qwen Streaming模型初始化成功: {}", dashscopeModel);
        }
    }

    @Bean
    public ChatModel qwenChatModel() {
        return qwenChatModel;
    }

    @Bean(name = "qwenVisionChatModel")
    public ChatModel qwenVisionChatModel() {
        return qwenVisionChatModel;
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return streamingChatModel;
    }

    @Bean(name = "allMiniLmL6V2EmbeddingModel")
    public EmbeddingModel allMiniLmL6V2EmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build();
        return new QdrantClient(grpcClient);
    }

    @Bean(name = "qdrantEmbeddingStore")
    public EmbeddingStore<TextSegment> qdrantEmbeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)
                .collectionName(collectionName)
                .build();
    }

    @Bean
    public WanxImageModel wanxImageModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty()) {
            log.warn("DashScope API Key未配置，WanxImageModel不可用");
            return null;
        }
        return WanxImageModel.builder()
                .apiKey(dashscopeApiKey)
                .build();
    }
}
