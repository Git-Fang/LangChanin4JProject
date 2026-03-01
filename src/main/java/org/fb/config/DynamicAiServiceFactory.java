package org.fb.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.fb.bean.ChatModelInfo;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.ChatAssistantStream;
import org.fb.service.assistant.TranslaterService;
import org.fb.tools.CommonTools;
import org.fb.tools.MongoChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态AiService工厂
 * 根据modelId动态获取不同的ChatModel Bean，支持运行时模型切换
 */
@Component
public class DynamicAiServiceFactory {
    private static final Logger log = LoggerFactory.getLogger(DynamicAiServiceFactory.class);

    @Autowired
    private LLMConfig llmConfig;

    @Autowired
    private ModelRegistry modelRegistry;

    @Autowired(required = false)
    @Qualifier("chatModel")
    private ChatModel defaultChatModel;

    @Autowired(required = false)
    @Qualifier("ollamaChatModel")
    private ChatModel ollamaChatModel;

    @Autowired(required = false)
    @Qualifier("qwenChatModel")
    private ChatModel qwenChatModel;

    @Autowired(required = false)
    @Qualifier("kimiChatModel")
    private ChatModel kimiChatModel;

    @Autowired(required = false)
    @Qualifier("qwenVisionChatModel")
    private ChatModel qwenVisionChatModel;

    @Autowired(required = false)
    @Qualifier("streamingChatModel")
    private StreamingChatModel streamingChatModel;

    @Autowired(required = false)
    @Qualifier("chatMemoryProvider")
    private ChatMemoryProvider chatMemoryProvider;

    @Autowired(required = false)
    private CommonTools commonTools;

    // ChatAssistant 缓存: key = modelId
    private final Map<String, ChatAssistant> chatAssistantCache = new ConcurrentHashMap<>();

    // ChatAssistantStream 缓存: key = modelId
    private final Map<String, ChatAssistantStream> chatAssistantStreamCache = new ConcurrentHashMap<>();

    // TranslaterService 缓存: key = modelId
    private final Map<String, TranslaterService> translaterServiceCache = new ConcurrentHashMap<>();

    /**
     * 获取动态ChatAssistant
     * 根据modelId选择对应的ChatModel
     */
    public ChatAssistant getChatAssistant(String modelId) {
        validateModel(modelId);
        
        return chatAssistantCache.computeIfAbsent(modelId, this::createChatAssistant);
    }

    /**
     * 获取动态流式ChatAssistant
     */
    public ChatAssistantStream getChatAssistantStream(String modelId) {
        validateModel(modelId);
        
        return chatAssistantStreamCache.computeIfAbsent(modelId, this::createChatAssistantStream);
    }

    /**
     * 获取动态TranslaterService
     */
    public TranslaterService getTranslaterService(String modelId) {
        validateModel(modelId);
        
        return translaterServiceCache.computeIfAbsent(modelId, this::createTranslaterService);
    }

    /**
     * 验证模型是否可用
     */
    private void validateModel(String modelId) {
        ChatModelInfo modelInfo = modelRegistry.getModel(modelId);
        if (modelInfo == null) {
            throw new IllegalArgumentException("模型不存在: " + modelId);
        }
        if (!modelInfo.isEnabled()) {
            throw new IllegalArgumentException("模型未启用: " + modelId);
        }
    }

    /**
     * 创建ChatAssistant实例
     * 注意：这里使用默认的ChatModel，实际项目中需要根据modelId动态选择
     */
    private ChatAssistant createChatAssistant(String modelId) {
        log.warn("动态AiServiceFactory createChatAssistant: modelId={}, 需要通过model选择器实现动态切换", modelId);
        
        // 这里返回默认的ChatAssistant，实际使用时应该根据modelId选择不同的模型
        // 由于LangChain4j的AiService是编译时绑定的，这里需要返回已有的Bean
        // 真正的动态切换需要使用动态代理或重新构建AiService
        throw new UnsupportedOperationException("动态ChatAssistant创建需要更复杂的实现，建议使用默认的ChatAssistant");
    }

    /**
     * 创建流式ChatAssistant实例
     */
    private ChatAssistantStream createChatAssistantStream(String modelId) {
        log.warn("动态AiServiceFactory createChatAssistantStream: modelId={}", modelId);
        throw new UnsupportedOperationException("动态ChatAssistantStream创建需要更复杂的实现");
    }

    /**
     * 创建TranslaterService实例
     */
    private TranslaterService createTranslaterService(String modelId) {
        log.warn("动态AiServiceFactory createTranslaterService: modelId={}", modelId);
        throw new UnsupportedOperationException("动态TranslaterService创建需要更复杂的实现");
    }

    /**
     * 根据modelId获取对应的ChatModel
     * 这个方法在实际运行时选择正确的模型
     */
    public ChatModel getChatModelById(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return defaultChatModel;
        }

        return switch (modelId.toLowerCase()) {
            case "deepseek" -> {
                // DeepSeek通过default ChatModel获取（因为配置在chatModel中）
                log.info("选择模型: deepseek");
                yield defaultChatModel != null ? defaultChatModel : qwenChatModel;
            }
            case "qwen" -> {
                log.info("选择模型: qwen");
                yield qwenChatModel;
            }
            case "ollama" -> {
                log.info("选择模型: ollama");
                yield ollamaChatModel;
            }
            case "kimi" -> {
                log.info("选择模型: kimi");
                yield kimiChatModel;
            }
            default -> {
                log.warn("未知模型: {}，使用默认模型", modelId);
                yield defaultChatModel;
            }
        };
    }

    /**
     * 获取默认ChatModel
     */
    public ChatModel getDefaultChatModel() {
        return defaultChatModel;
    }

    /**
     * 清除指定模型的缓存
     */
    public void clearCache(String modelId) {
        log.info("清除模型 {} 的缓存", modelId);
        
        chatAssistantCache.remove(modelId);
        chatAssistantStreamCache.remove(modelId);
        translaterServiceCache.remove(modelId);
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        log.info("清除所有模型缓存");
        
        chatAssistantCache.clear();
        chatAssistantStreamCache.clear();
        translaterServiceCache.clear();
    }

    /**
     * 刷新所有模型
     */
    public void refreshAllModels() {
        log.info("刷新所有模型配置");
        
        // 重新加载LLM配置
        llmConfig.refreshAllModels();
        
        // 重新初始化模型注册表
        modelRegistry.refreshModels();
        
        // 清除所有缓存
        clearAllCache();
        
        log.info("模型刷新完成");
    }

    /**
     * 获取缓存的AiService数量
     */
    public int getCacheSize() {
        return chatAssistantCache.size() + chatAssistantStreamCache.size() + translaterServiceCache.size();
    }
}
