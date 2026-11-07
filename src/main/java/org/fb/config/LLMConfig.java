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

    /**
     * 走网关（new-api）时置 true，Qwen 流式改用 OpenAI 兼容客户端；
     * 为 false 时保持原生 QwenStreamingChatModel（直连 DashScope）。
     */
    @Value("${ai.dashscope.use-openai-streaming:false}")
    private volatile boolean dashscopeUseOpenAiStreaming;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private volatile String ollamaUrl;

    @Value("${ai.ollama.model:deepseek-r1:8b}")
    private volatile String ollamaModel;

    /**
     * 非空时 Ollama 请求走 OpenAI 兼容网关（如 new-api:3002），便于统计用量；
     * 为空时保持原行为，直连 Ollama 原生接口。
     */
    @Value("${ai.ollama.api-key:}")
    private volatile String ollamaApiKey;

    @Value("${ai.deepSeek.apiKey:}")
    private volatile String deepSeekApiKey;

    @Value("${ai.deepSeek.model:deepseek-v4.1-flash-1017}")
    private volatile String deepSeekModel;

    @Value("${ai.deepSeek.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private volatile String deepSeekUrl;

    @Value("${ai.deepSeek.max-retries:3}")
    private volatile int deepSeekMaxRetries;

    @Value("${ai.default-model:deepseek}")
    private volatile String defaultModelId;

    @Value("${ai.kimi.model:kimi-k2.6}")
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

    /** 非流式动态模型代理，供 AiService 跟随用户选择的模型 */
    private volatile DynamicChatModel dynamicChatModel;

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
        refreshDynamicChatModel();
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
        
        // 设置默认模型（可通过 ai.default-model 配置，默认 deepseek）
        String targetDefault = (defaultModelId == null || defaultModelId.isEmpty()) ? "deepseek" : defaultModelId;
        if (!dynamicStreamingChatModel.hasModel(targetDefault)) {
            log.warn("【动态流式模型】默认模型 {} 未注册，回退到 deepseek", targetDefault);
            targetDefault = "deepseek";
        }
        dynamicStreamingChatModel.setCurrentModel(targetDefault);
        
        log.info("【动态流式模型】初始化完成，已注册模型: {}", dynamicStreamingChatModel.getModels().keySet());
    }

    /**
     * 初始化非流式动态模型代理（供 DoctorAgent、翻译、术语提取等 AiService 使用），
     * 使这些业务也能跟随用户在页面上选择的模型
     */
    private void refreshDynamicChatModel() {
        this.dynamicChatModel = new DynamicChatModel();

        if (qwenChatModel != null) {
            dynamicChatModel.registerModel("qwen", qwenChatModel);
        }
        if (deepSeekChatModel != null) {
            dynamicChatModel.registerModel("deepseek", deepSeekChatModel);
        }
        if (ollamaChatModel != null) {
            dynamicChatModel.registerModel("ollama", ollamaChatModel);
        }
        if (kimiChatModel != null) {
            dynamicChatModel.registerModel("kimi", kimiChatModel);
        }

        String targetDefault = (defaultModelId == null || defaultModelId.isEmpty()) ? "deepseek" : defaultModelId;
        if (!dynamicChatModel.hasModel(targetDefault)) {
            log.warn("【动态模型-非流式】默认模型 {} 未注册，回退到第一个可用模型", targetDefault);
        } else {
            dynamicChatModel.setCurrentModel(targetDefault);
        }

        log.info("【动态模型-非流式】初始化完成，已注册模型: {}", dynamicChatModel.getModels().keySet());
    }

    private void refreshDeepSeekChatModel() {
        if (deepSeekApiKey == null || deepSeekApiKey.isEmpty()) {
            log.warn("DeepSeek API Key未配置，DeepSeek模型不可用");
            this.deepSeekChatModel = null;
        } else {
            this.deepSeekChatModel = OpenAiChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
            } else if (dashscopeUseOpenAiStreaming) {
                // 经网关（new-api）转发，使用 OpenAI 兼容流式客户端，便于统计用量
                this.streamingChatModel = OpenAiStreamingChatModel.builder()
                        .listeners(org.fb.trace.LangfuseTracer.listeners())
                        .apiKey(dashscopeApiKey)
                        .modelName(dashscopeModel)
                        .baseUrl(dashscopeUrl)
                        .timeout(READ_TIMEOUT)
                        .logRequests(true)
                        .logResponses(true)
                        .build();
                log.info("Qwen Streaming模型通过网关初始化成功: {} @ {}", dashscopeModel, dashscopeUrl);
            } else {
                // 使用DashScope原生的QwenStreamingChatModel，避免OpenAI兼容模式的序列化问题
                                this.streamingChatModel = QwenStreamingChatModel.builder()
                                        .apiKey(dashscopeApiKey)
                                        .modelName(dashscopeModel)
                                        .build();
                log.info("Qwen Streaming模型直连初始化成功: {}", dashscopeModel);
            }
        }
    
    private void refreshDeepSeekStreamingChatModel() {
        if (deepSeekApiKey == null || deepSeekApiKey.isEmpty()) {
            log.warn("DeepSeek API Key未配置，DeepSeek Streaming模型不可用");
            this.deepSeekStreamingChatModel = null;
        } else {
            this.deepSeekStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
        if (ollamaApiKey != null && !ollamaApiKey.isEmpty()) {
            // 走 OpenAI 兼容网关（new-api），可在网关侧统计用量
            this.ollamaStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
                    .apiKey(ollamaApiKey)
                    .modelName(ollamaModel)
                    .baseUrl(ollamaUrl)
                    .temperature(0.8)
                    .timeout(READ_TIMEOUT)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            log.info("Ollama Streaming模型通过网关初始化成功: {} @ {}", ollamaModel, ollamaUrl);
        } else {
            this.ollamaStreamingChatModel = OllamaStreamingChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
                    .baseUrl(ollamaUrl)
                    .modelName(ollamaModel)
                    .temperature(0.8)
                    .timeout(READ_TIMEOUT)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            log.info("Ollama Streaming模型直连初始化成功: {}", ollamaModel);
        }
    }
    
    private void refreshKimiStreamingChatModel() {
        if (kimiApiKey == null || kimiApiKey.isEmpty()) {
            log.warn("Kimi API Key未配置，Kimi Streaming模型不可用");
            this.kimiStreamingChatModel = null;
        } else {
            this.kimiStreamingChatModel = OpenAiStreamingChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
        if (ollamaApiKey != null && !ollamaApiKey.isEmpty()) {
            // 走 OpenAI 兼容网关（new-api），可在网关侧统计用量
            this.ollamaChatModel = OpenAiChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
                    .apiKey(ollamaApiKey)
                    .modelName(ollamaModel)
                    .baseUrl(ollamaUrl)
                    .temperature(0.8)
                    .timeout(READ_TIMEOUT)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            log.info("Ollama模型通过网关初始化成功: {} @ {}", ollamaModel, ollamaUrl);
        } else {
            this.ollamaChatModel = OllamaChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
                    .baseUrl(ollamaUrl)
                    .modelName(ollamaModel)
                    .temperature(0.8)
                    .timeout(READ_TIMEOUT)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            log.info("Ollama模型直连初始化成功: {}", ollamaModel);
        }
    }

    private void refreshKimiChatModel() {
        if (kimiApiKey == null || kimiApiKey.isEmpty()) {
            log.warn("Kimi API Key未配置，Kimi模型不可用");
            this.kimiChatModel = null;
        } else {
            this.kimiChatModel = OpenAiChatModel.builder()
                    .listeners(org.fb.trace.LangfuseTracer.listeners())
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
        // 优先使用 Qwen，不可用时 fallback 到 Ollama
        if (qwenChatModel != null) {
            return qwenChatModel;
        }
        log.warn("Qwen ChatModel 不可用，fallback 到 Ollama ChatModel");
        return ollamaChatModel;
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
        // 优先使用 Qwen Vision，不可用时 fallback 到 Ollama ChatModel
        if (qwenVisionChatModel != null) {
            return qwenVisionChatModel;
        }
        log.warn("Qwen Vision ChatModel 不可用，fallback 到 Ollama ChatModel");
        return ollamaChatModel;
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        // 优先使用 Qwen Streaming，不可用时 fallback 到 Ollama Streaming
        if (streamingChatModel != null) {
            return streamingChatModel;
        }
        log.warn("Qwen StreamingChatModel 不可用，fallback 到 Ollama StreamingChatModel");
        return ollamaStreamingChatModel;
    }
    
    @Bean(name = "dynamicStreamingChatModel")
    public DynamicStreamingChatModel dynamicStreamingChatModel() {
        return dynamicStreamingChatModel;
    }

    @Bean(name = "dynamicChatModel")
    public ChatModel dynamicChatModel() {
        return dynamicChatModel;
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
