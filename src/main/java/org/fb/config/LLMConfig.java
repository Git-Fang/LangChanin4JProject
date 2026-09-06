package org.fb.config;

import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.annotation.PostConstruct;
import org.fb.service.assistant.NaturalLanguageSQLAgent;
import org.fb.tools.MongoChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@RefreshScope
public class LLMConfig {
    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    @Value("${ai.dashscope.apiKey:demo}")
    private volatile String dashscopeApiKey;

    @Value("${ai.dashscope.model:qwen-max}")
    private volatile String dashscopeModel;

    @Value("${ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private volatile String dashscopeUrl;

    @Value("${ai.dashscope.max-retries:3}")
    private volatile int dashscopeMaxRetries;

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

    @Value("${ai.deepSeek.max-retries:3}")
    private volatile int deepSeekMaxRetries;

    @Value("${ai.kimi.model:kimi-k2-turbo-preview}")
    private volatile String kimiModel;

    @Value("${ai.kimi.apiKey:}")
    private volatile String kimiApiKey;

    @Value("${ai.kimi.base-url:https://api.moonshot.cn/v1}")
    private volatile String kimiUrl;

    @Value("${ai.kimi.max-retries:3}")
    private volatile int kimiMaxRetries;

    @Value("${ai.embeddingStore.qdrant.host:localhost}")
    private volatile String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port:6334}")
    private volatile Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName:ragTranslation-1226}")
    private volatile String collectionName;

    @Autowired
    private volatile MongoChatMemoryStore mongoChatMemoryStore;

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private volatile ChatModel deepSeekChatModel;
    private volatile ChatModel qwenChatModel;
    private volatile ChatModel qwenVisionChatModel;
    private volatile StreamingChatModel streamingChatModel;
    private volatile StreamingChatModel deepSeekStreamingChatModel;
    private volatile StreamingChatModel ollamaStreamingChatModel;
    private volatile StreamingChatModel kimiStreamingChatModel;
    private volatile ChatModel ollamaChatModel;
    private volatile ChatModel kimiChatModel;
    private volatile DynamicStreamingChatModel dynamicStreamingChatModel;

    @PostConstruct
    public void init() {
        refreshAllModels();
    }

    public void refreshAllModels() {
        refreshDeepSeekChatModel();
        refreshQwenChatModel();
        refreshQwenVisionChatModel();
        refreshStreamingChatModel();
        refreshDeepSeekStreamingChatModel();
        refreshOllamaStreamingChatModel();
        refreshKimiStreamingChatModel();
        refreshOllamaChatModel();
        refreshKimiChatModel();
        refreshDynamicStreamingChatModel();
        log.info("All chat models refreshed successfully");
    }
    
    /**
     * 初始化动态流式模型代理
     */
    private void refreshDynamicStreamingChatModel() {
        this.dynamicStreamingChatModel = new DynamicStreamingChatModel();
        
        // 注册各模型
        if (streamingChatModel != null) {
            dynamicStreamingChatModel.registerModel("qwen", streamingChatModel);
        }
        if (deepSeekStreamingChatModel != null) {
            dynamicStreamingChatModel.registerModel("deepseek", deepSeekStreamingChatModel);
        }
        if (ollamaStreamingChatModel != null) {
            dynamicStreamingChatModel.registerModel("ollama", ollamaStreamingChatModel);
        }
        if (kimiStreamingChatModel != null) {
            dynamicStreamingChatModel.registerModel("kimi", kimiStreamingChatModel);
        }
        
        // 设置默认模型
        dynamicStreamingChatModel.setCurrentModel("qwen");
        
        log.info("【动态流式模型】初始化完成，已注册模型: {}", dynamicStreamingChatModel.getModels().keySet());
    }

    private void refreshDeepSeekChatModel() {
        if (deepSeekApiKey == null || deepSeekApiKey.isEmpty()) {
            log.warn("DeepSeek API Key未配置，DeepSeek模型不可用");
            this.deepSeekChatModel = null;
        } else {
            this.deepSeekChatModel = OpenAiChatModel.builder()
                    .apiKey(deepSeekApiKey)
                    .modelName(deepSeekModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(deepSeekUrl)
                    .timeout(READ_TIMEOUT)
                    .maxRetries(deepSeekMaxRetries)
                    .build();
        }
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
                    .timeout(READ_TIMEOUT)
                    .maxRetries(dashscopeMaxRetries)
                    .build();
        }
    }

    private void refreshQwenVisionChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
            log.warn("DashScope API Key未配置，Qwen Vision模型不可用");
            this.qwenVisionChatModel = null;
        } else {
            // 使用支持视觉能力的qwen-vl模型
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
            if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
                log.warn("DashScope API Key未配置，Streaming模型不可用");
                this.streamingChatModel = null;
            } else {
                // 使用DashScope原生的QwenStreamingChatModel，避免OpenAI兼容模式的序列化问题
                                this.streamingChatModel = QwenStreamingChatModel.builder()
                                        .apiKey(dashscopeApiKey)
                                        .modelName(dashscopeModel)
                                        .build();
            }
        }
    
    private void refreshDeepSeekStreamingChatModel() {
        if (deepSeekApiKey == null || deepSeekApiKey.isEmpty()) {
            log.warn("DeepSeek API Key未配置，DeepSeek Streaming模型不可用");
            this.deepSeekStreamingChatModel = null;
        } else {
            this.deepSeekStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .apiKey(deepSeekApiKey)
                    .modelName(deepSeekModel)
                    .baseUrl(deepSeekUrl)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(READ_TIMEOUT)
                    .build();
            log.info("DeepSeek Streaming模型初始化成功: {}", deepSeekModel);
        }
    }
    
    private void refreshOllamaStreamingChatModel() {
        this.ollamaStreamingChatModel = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(ollamaModel)
                .temperature(0.8)
                .timeout(READ_TIMEOUT)
                .logRequests(true)
                .logResponses(true)
                .build();
        log.info("Ollama Streaming模型初始化成功: {}", ollamaModel);
    }
    
    private void refreshKimiStreamingChatModel() {
        if (kimiApiKey == null || kimiApiKey.isEmpty()) {
            log.warn("Kimi API Key未配置，Kimi Streaming模型不可用");
            this.kimiStreamingChatModel = null;
        } else {
            this.kimiStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .apiKey(kimiApiKey)
                    .modelName(kimiModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(kimiUrl)
                    .timeout(READ_TIMEOUT)
                    .build();
            log.info("Kimi Streaming模型初始化成功: {}", kimiModel);
        }
    }

    private void refreshOllamaChatModel() {
        this.ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName(ollamaModel)
                .temperature(0.8)
                .timeout(READ_TIMEOUT)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private void refreshKimiChatModel() {
        if (kimiApiKey == null || kimiApiKey.isEmpty()) {
            log.warn("Kimi API Key未配置，Kimi模型不可用");
            this.kimiChatModel = null;
        } else {
            this.kimiChatModel = OpenAiChatModel.builder()
                    .apiKey(kimiApiKey)
                    .modelName(kimiModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(kimiUrl)
                    .timeout(READ_TIMEOUT)
                    .build();
        }
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

    @Bean(name = "qwenVisionChatModel")
    public ChatModel qwenVisionChatModel() {
        return qwenVisionChatModel;
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return streamingChatModel;
    }
    
    @Bean(name = "dynamicStreamingChatModel")
    public DynamicStreamingChatModel dynamicStreamingChatModel() {
        return dynamicStreamingChatModel;
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
        return memoryId -> {
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);  // 支持约5轮对话（用户+AI各1条=2条消息/轮）
            
            // 从MongoDB加载历史消息
            if (mongoChatMemoryStore != null) {
                try {
                    List<ChatMessage> historyMessages = mongoChatMemoryStore.getMessages(memoryId);
                    if (historyMessages != null && !historyMessages.isEmpty()) {
                        log.info("从MongoDB加载对话历史, memoryId: {}, 消息数量: {}", memoryId, historyMessages.size());
                        historyMessages.forEach(chatMemory::add);
                    }
                } catch (Exception e) {
                    log.error("从MongoDB加载对话历史失败, memoryId: {}", memoryId, e);
                }
            }
            
            return chatMemory;
        };
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
