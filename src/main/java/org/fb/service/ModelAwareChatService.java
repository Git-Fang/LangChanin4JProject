package org.fb.service;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.fb.config.LLMConfig;
import org.fb.context.ModelContext;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.ChatTypeAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 模型感知聊天服务 - 支持动态切换大模型
 * 根据ModelContext中设置的模型ID，动态选择对应的ChatModel进行聊天
 */
@Service
public class ModelAwareChatService {

    private static final Logger log = LoggerFactory.getLogger(ModelAwareChatService.class);

    private static final String DEFAULT_MODEL = "qwen";

    @Autowired
    private LLMConfig llmConfig;

    @Autowired(required = false)
    @Qualifier("chatMemoryProvider")
    private dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider;

    @Autowired(required = false)
    @Qualifier("contentRetriever")
    private ContentRetriever contentRetriever;

    private ChatTypeAssistant deepseekChatTypeAssistant;
    private ChatTypeAssistant qwenChatTypeAssistant;
    private ChatTypeAssistant kimiChatTypeAssistant;
    private ChatTypeAssistant ollamaChatTypeAssistant;

    private ChatAssistant deepseekChatAssistant;
    private ChatAssistant qwenChatAssistant;
    private ChatAssistant kimiChatAssistant;
    private ChatAssistant ollamaChatAssistant;

    @PostConstruct
    public void init() {
        // 为每个模型创建对应的AI服务
        ChatModel deepseekModel = llmConfig.getChatModel("deepseek");
        ChatModel qwenModel = llmConfig.getChatModel("qwen");
        ChatModel kimiModel = llmConfig.getChatModel("kimi");
        ChatModel ollamaModel = llmConfig.getChatModel("ollama");

        if (deepseekModel != null) {
            deepseekChatTypeAssistant = createChatTypeAssistant(deepseekModel);
            deepseekChatAssistant = createChatAssistant(deepseekModel);
            log.info("DeepSeek AI服务初始化成功");
        }

        if (qwenModel != null) {
            qwenChatTypeAssistant = createChatTypeAssistant(qwenModel);
            qwenChatAssistant = createChatAssistant(qwenModel);
            log.info("Qwen AI服务初始化成功");
        }

        if (kimiModel != null) {
            kimiChatTypeAssistant = createChatTypeAssistant(kimiModel);
            kimiChatAssistant = createChatAssistant(kimiModel);
            log.info("Kimi AI服务初始化成功");
        }

        if (ollamaModel != null) {
            ollamaChatTypeAssistant = createChatTypeAssistant(ollamaModel);
            ollamaChatAssistant = createChatAssistant(ollamaModel);
            log.info("Ollama AI服务初始化成功");
        }
    }

    /**
     * 使用指定模型进行意图识别
     * @param modelId 模型ID
     * @param memoryId 记忆ID
     * @param userMessage 用户消息
     * @return AI响应
     */
    public String chatWithModel(String modelId, long memoryId, String userMessage) {
        String actualModelId = (modelId == null || modelId.isEmpty()) ? DEFAULT_MODEL : modelId;
        log.info("使用模型进行意图识别: {}, memoryId: {}", actualModelId, memoryId);

        ChatTypeAssistant assistant = getChatTypeAssistant(actualModelId);
        if (assistant == null) {
            log.warn("模型 {} 不可用，使用默认模型", actualModelId);
            assistant = qwenChatTypeAssistant;
            if (assistant == null) {
                return "抱歉，当前模型不可用，请稍后重试。";
            }
        }

        return assistant.chat(userMessage);
    }

    /**
     * 使用指定模型进行普通聊天
     * @param modelId 模型ID
     * @param memoryId 记忆ID
     * @param userMessage 用户消息
     * @return AI响应
     */
    public String chatGeneralWithModel(String modelId, long memoryId, String userMessage) {
        String actualModelId = (modelId == null || modelId.isEmpty()) ? DEFAULT_MODEL : modelId;
        log.info("使用模型进行普通聊天: {}, memoryId: {}", actualModelId, memoryId);

        ChatAssistant assistant = getChatAssistant(actualModelId);
        if (assistant == null) {
            log.warn("模型 {} 不可用，使用默认模型", actualModelId);
            assistant = qwenChatAssistant;
            if (assistant == null) {
                return "抱歉，当前模型不可用，请稍后重试。";
            }
        }

        return assistant.chat(memoryId, userMessage);
    }

    /**
     * 获取指定模型的ChatTypeAssistant
     */
    private ChatTypeAssistant getChatTypeAssistant(String modelId) {
        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepseekChatTypeAssistant;
            case "qwen":
                return qwenChatTypeAssistant;
            case "kimi":
                return kimiChatTypeAssistant;
            case "ollama":
                return ollamaChatTypeAssistant;
            default:
                return qwenChatTypeAssistant;
        }
    }

    /**
     * 获取指定模型的ChatAssistant
     */
    private ChatAssistant getChatAssistant(String modelId) {
        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepseekChatAssistant;
            case "qwen":
                return qwenChatAssistant;
            case "kimi":
                return kimiChatAssistant;
            case "ollama":
                return ollamaChatAssistant;
            default:
                return qwenChatAssistant;
        }
    }

    /**
     * 创建动态ChatTypeAssistant
     */
    private ChatTypeAssistant createChatTypeAssistant(ChatModel chatModel) {
        var builder = AiServices.builder(ChatTypeAssistant.class)
                .chatModel(chatModel);

        // 配置ChatMemoryProvider（必须配置，因为ChatTypeAssistant使用了@MemoryId）
        if (chatMemoryProvider != null) {
            builder.chatMemoryProvider(chatMemoryProvider);
        } else {
            // 如果没有配置ChatMemoryProvider，创建一个简单的基于内存的
            log.warn("ChatMemoryProvider未配置，使用简单的内存ChatMemoryProvider");
            builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));
        }

        return builder.build();
    }

    /**
     * 创建动态ChatAssistant
     */
    private ChatAssistant createChatAssistant(ChatModel chatModel) {
        var builder = AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel);

        // 配置ChatMemoryProvider
        if (chatMemoryProvider != null) {
            builder.chatMemoryProvider(chatMemoryProvider);
        } else {
            // 如果没有配置ChatMemoryProvider，创建一个简单的基于内存的
            log.warn("ChatMemoryProvider未配置，使用简单的内存ChatMemoryProvider");
            builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10));
        }

        return builder.build();
    }

    /**
     * 刷新所有AI服务（当配置变化时调用）
     */
    public void refreshAllModels() {
        init();
    }

    /**
     * 获取当前选择的模型ID
     * @return 模型ID
     */
    public String getSelectedModel() {
        String modelId = ModelContext.getModel();
        return modelId != null ? modelId : DEFAULT_MODEL;
    }
}
