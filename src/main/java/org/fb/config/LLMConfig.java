package org.fb.config;

import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RefreshScope
public class LLMConfig {
    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    @Value("${ai.dashscope.apiKey:demo}")
    private volatile String dashscopeApiKey;

    @Value("${ai.dashscope.model:qwen-vl-max}")
    private volatile String dashscopeModel;

    @Value("${ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private volatile String dashscopeUrl;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private volatile String ollamaUrl;

    @Value("${ai.ollama.model:deepseek-r1:8b}")
    private volatile String ollamaModel;

    @Value("${ai.deepSeek.apiKey:}")
    private volatile String deepSeekApiKey;

    @Value("${ai.deepSeek.model:deepseek-chat}")
    private volatile String deepSeekModel;

    @Value("${ai.deepSeek.base-url:https://api.deepseek.com/v1}")
    private volatile String deepSeekUrl;

    @Value("${ai.kimi.model:kimi-k2-turbo-preview}")
    private volatile String kimiModel;

    @Value("${ai.kimi.base-url:https://api.moonshot.cn/v1}")
    private volatile String kimiUrl;

    @Value("${ai.embeddingStore.qdrant.host:localhost}")
    private volatile String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port:6334}")
    private volatile Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName:ragTranslation-1226}")
    private volatile String collectionName;

    private volatile ChatModel deepSeekChatModel;
    private volatile ChatModel qwenChatModel;
    private volatile StreamingChatModel streamingChatModel;
    private volatile ChatModel ollamaChatModel;
    private volatile ChatModel kimiChatModel;

    @PostConstruct
    public void init() {
        refreshAllModels();
    }

    public void refreshAllModels() {
        refreshDeepSeekChatModel();
        refreshQwenChatModel();
        refreshStreamingChatModel();
        refreshOllamaChatModel();
        refreshKimiChatModel();
        log.info("All chat models refreshed successfully");
    }

    private void refreshDeepSeekChatModel() {
        this.deepSeekChatModel = OpenAiChatModel.builder()
                .apiKey(deepSeekApiKey)
                .modelName(deepSeekModel)
                .logRequests(true)
                .logResponses(true)
                .baseUrl(deepSeekUrl)
                .build();
    }

    private void refreshQwenChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
            log.warn("DashScope API Key未配置，Qwen模型不可用");
            this.qwenChatModel = null;
        } else {
            this.qwenChatModel = OpenAiChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .baseUrl(dashscopeUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }
    }

    private void refreshStreamingChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
            log.warn("DashScope API Key未配置，Streaming模型不可用");
            this.streamingChatModel = null;
        } else {
            this.streamingChatModel = OpenAiStreamingChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(dashscopeUrl)
                    .build();
        }
    }

    private void refreshOllamaChatModel() {
        this.ollamaChatModel = dev.langchain4j.model.ollama.OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(ollamaModel)
                .temperature(0.8)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private void refreshKimiChatModel() {
        this.kimiChatModel = OpenAiChatModel.builder()
                .apiKey(kimiModel)
                .modelName(kimiModel)
                .logRequests(true)
                .logResponses(true)
                .baseUrl(kimiUrl)
                .build();
    }

    @Bean
    public ChatModel chatModel() {
        return qwenChatModel;
    }

    @Bean
    public ChatModel ollamaChatModel() {
        return ollamaChatModel;
    }

    @Bean
    public ChatModel qwenChatModel() {
        return qwenChatModel;
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return streamingChatModel;
    }

    @Bean
    public ChatModel kimiChatModel() {
        return kimiChatModel;
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

    @Bean(name = "chatMemoryProvider")
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(10);
    }

    @Bean
    public WanxImageModel wanxImageModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
            log.warn("DashScope API Key未配置，WanxImageModel不可用");
            return null;
        }
        return WanxImageModel.builder()
                .apiKey(dashscopeApiKey)
                .build();
    }
}
