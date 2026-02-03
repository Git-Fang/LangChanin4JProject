package org.fb.config;

import dev.langchain4j.community.model.dashscope.WanxImageModel;
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
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.annotation.PostConstruct;
import org.fb.bean.ModelInfo;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private volatile StreamingChatModel qwenStreamingChatModel;
    private volatile ChatModel ollamaChatModel;
    private volatile ChatModel kimiChatModel;

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
        refreshQwenStreamingChatModel();
        refreshOllamaChatModel();
        refreshKimiChatModel();
        log.info("All chat models refreshed successfully");
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
            this.streamingChatModel = OpenAiStreamingChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(dashscopeUrl)
                    .timeout(READ_TIMEOUT)
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
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(deepSeekUrl)
                    .timeout(READ_TIMEOUT)
                    .build();
        }
    }

    private void refreshQwenStreamingChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || dashscopeApiKey.equals("demo")) {
            log.warn("DashScope API Key未配置，Qwen Streaming模型不可用");
            this.qwenStreamingChatModel = null;
        } else {
            this.qwenStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .apiKey(dashscopeApiKey)
                    .modelName(dashscopeModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(dashscopeUrl)
                    .timeout(READ_TIMEOUT)
                    .build();
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
        if (kimiModel == null || kimiModel.isEmpty()) {
            log.warn("Kimi API Key未配置，Kimi模型不可用");
            this.kimiChatModel = null;
        } else {
            this.kimiChatModel = OpenAiChatModel.builder()
                    .apiKey(kimiModel)
                    .modelName(kimiModel)
                    .logRequests(true)
                    .logResponses(true)
                    .baseUrl(kimiUrl)
                    .timeout(READ_TIMEOUT)
                    .maxRetries(kimiMaxRetries)
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

    @Bean
    public StreamingChatModel deepSeekStreamingChatModel() {
        return deepSeekStreamingChatModel;
    }

    @Bean
    public StreamingChatModel qwenStreamingChatModel() {
        return qwenStreamingChatModel;
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
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            
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

    /**
     * 获取所有可用的大模型列表
     * @return 模型信息列表
     */
    public List<ModelInfo> getAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();

        // Qwen (默认模型)
        boolean qwenAvailable = qwenChatModel != null;
        models.add(ModelInfo.createQwen(dashscopeModel, dashscopeUrl, qwenAvailable));

        // DeepSeek
        boolean deepSeekAvailable = deepSeekChatModel != null;
        models.add(ModelInfo.createDeepSeek(deepSeekModel, deepSeekUrl, deepSeekAvailable));

        // Kimi
        boolean kimiAvailable = kimiChatModel != null;
        models.add(ModelInfo.createKimi(kimiModel, kimiUrl, kimiAvailable));

        // Ollama
        boolean ollamaAvailable = ollamaChatModel != null;
        models.add(ModelInfo.createOllama(ollamaModel, ollamaUrl, ollamaAvailable));

        return models;
    }

    /**
     * 根据模型ID获取对应的ChatModel
     * @param modelId 模型ID (deepseek, qwen, kimi, ollama)
     * @return 对应的ChatModel，如果未配置则返回默认的qwenChatModel
     */
    public ChatModel getChatModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return qwenChatModel;
        }

        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepSeekChatModel;
            case "qwen":
                return qwenChatModel;
            case "kimi":
                return kimiChatModel;
            case "ollama":
                return ollamaChatModel;
            default:
                log.warn("未知的模型ID: {}，使用默认模型", modelId);
                return qwenChatModel;
        }
    }

    /**
      * 检查指定模型是否可用
      * @param modelId 模型ID
      * @return 是否可用
      */
    public boolean isModelAvailable(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return qwenChatModel != null;
        }

        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepSeekChatModel != null;
            case "qwen":
                return qwenChatModel != null;
            case "kimi":
                return kimiChatModel != null;
            case "ollama":
                return ollamaChatModel != null;
            default:
                return false;
        }
    }

    /**
     * 根据模型ID获取对应的StreamingChatModel
     * @param modelId 模型ID (deepseek, qwen, kimi, ollama)
     * * @return 对应的StreamingChatModel，如果未配置则返回默认的qwenStreamingChatModel
     */
    public StreamingChatModel getStreamingChatModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return qwenStreamingChatModel;
        }

        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepSeekStreamingChatModel;
            case "qwen":
                return qwenStreamingChatModel;
            // 其他模型暂时不支持流式，返回Qwen
            default:
                log.warn("模型 {} 暂不支持流式切换，使用Qwen流式模型", modelId);
                return qwenStreamingChatModel;
        }
    }
}
