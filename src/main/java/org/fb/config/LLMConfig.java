package org.fb.config;

import dev.langchain4j.community.model.dashscope.WanxImageModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
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
    private volatile boolean modelsInitialized = false;

    public LLMConfig() {
        // 构造函数中不初始化，等待 @PostConstruct 确保依赖注入完成
    }

    @PostConstruct
    public synchronized void init() {
        if (modelsInitialized) {
            return;
        }
        log.info("开始初始化LLM模型, API Key: {}", dashscopeApiKey != null ? "已配置" : "未配置");
        refreshQwenChatModel();
        refreshQwenVisionChatModel();
        refreshStreamingChatModel();
        modelsInitialized = true;
        log.info("LLM models initialization completed");
    }

    private void refreshQwenChatModel() {
        log.info("检查DashScope API Key配置: {}", dashscopeApiKey != null ? "已注入" : "null");
        
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || "your-api-key-here".equals(dashscopeApiKey)) {
            log.warn("DashScope API Key未配置或无效，Qwen模型不可用");
            log.warn("当前配置值: dashscopeApiKey={}", dashscopeApiKey);
            this.qwenChatModel = createFallbackChatModel("Qwen");
        } else {
            try {
                log.info("正在初始化Qwen Chat模型, model={}, baseUrl={}", dashscopeModel, dashscopeUrl);
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
            } catch (Exception e) {
                log.error("Qwen模型初始化失败: {}, 使用fallback模型", e.getMessage(), e);
                this.qwenChatModel = createFallbackChatModel("Qwen");
            }
        }
    }

    private void refreshQwenVisionChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || "your-api-key-here".equals(dashscopeApiKey)) {
            log.warn("DashScope API Key未配置，Qwen Vision模型不可用");
            this.qwenVisionChatModel = createFallbackChatModel("QwenVision");
        } else {
            try {
                this.qwenVisionChatModel = OpenAiChatModel.builder()
                        .apiKey(dashscopeApiKey)
                        .modelName("qwen-vl-max")
                        .baseUrl(dashscopeUrl)
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(READ_TIMEOUT)
                        .maxRetries(dashscopeMaxRetries)
                        .build();
                log.info("Qwen Vision模型初始化成功");
            } catch (Exception e) {
                log.error("Qwen Vision模型初始化失败: {}, 使用fallback模型", e.getMessage());
                this.qwenVisionChatModel = createFallbackChatModel("QwenVision");
            }
        }
    }

    private void refreshStreamingChatModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || "your-api-key-here".equals(dashscopeApiKey)) {
            log.warn("DashScope API Key未配置，Streaming模型不可用");
            this.streamingChatModel = null;
        } else {
            try {
                this.streamingChatModel = QwenStreamingChatModel.builder()
                        .apiKey(dashscopeApiKey)
                        .modelName(dashscopeModel)
                        .build();
                log.info("Qwen Streaming模型初始化成功: {}", dashscopeModel);
            } catch (Exception e) {
                log.error("Qwen Streaming模型初始化失败: {}", e.getMessage());
                this.streamingChatModel = null;
            }
        }
    }

    /**
     * 创建Fallback ChatModel
     * 当真实模型不可用时返回，用于确保应用能够启动
     */
    private ChatModel createFallbackChatModel(String modelType) {
        String errorMessage = "【" + modelType + "】模型未正确配置或初始化失败。\n" +
                "请检查以下配置：\n" +
                "1. 环境变量 DASHSCOPE_API_KEY 是否已设置\n" +
                "2. API Key 是否有效且未过期\n" +
                "3. 网络连接是否正常\n\n" +
                "如已配置密钥但仍报错，请查看上方日志中的初始化失败原因。";
        
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                throw new UnsupportedOperationException(errorMessage);
            }
            
            @Override
            public String chat(String userMessage) {
                throw new UnsupportedOperationException(errorMessage);
            }
        };
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
        try {
            return new AllMiniLmL6V2EmbeddingModel();
        } catch (Exception e) {
            log.error("Embedding模型初始化失败: {}", e.getMessage());
            throw new RuntimeException("无法初始化Embedding模型，请检查ONNX运行时环境", e);
        }
    }

    @Bean
    public WanxImageModel wanxImageModel() {
        if (dashscopeApiKey == null || dashscopeApiKey.isEmpty() || "your-api-key-here".equals(dashscopeApiKey)) {
            log.warn("DashScope API Key未配置，WanxImageModel不可用");
            return null;
        }
        try {
            return WanxImageModel.builder()
                    .apiKey(dashscopeApiKey)
                    .build();
        } catch (Exception e) {
            log.warn("WanxImageModel初始化失败: {}", e.getMessage());
            return null;
        }
    }
}
