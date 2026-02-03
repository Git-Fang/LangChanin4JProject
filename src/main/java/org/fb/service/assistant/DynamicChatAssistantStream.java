package org.fb.service.assistant;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.fb.config.LLMConfig;
import org.fb.context.ModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;

/**
 * 动态流式聊天助手服务
 * 根据ModelContext中设置的模型ID，动态选择对应的StreamingChatModel进行流式对话
 */
@Service
public class DynamicChatAssistantStream {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatAssistantStream.class);
    private static final String DEFAULT_MODEL = "qwen";

    @Autowired
    private LLMConfig llmConfig;

    @Autowired(required = false)
    @Qualifier("chatMemoryProvider")
    private ChatMemoryProvider chatMemoryProvider;

    @Autowired(required = false)
    @Qualifier("contentRetriever")
    private ContentRetriever contentRetriever;

    private ChatAssistantStream qwenAssistant;
    private ChatAssistantStream deepseekAssistant;

    @PostConstruct
    public void init() {
        StreamingChatModel qwenModel = llmConfig.getStreamingChatModel("qwen");
        StreamingChatModel deepseekModel = llmConfig.getStreamingChatModel("deepseek");

        log.info("DynamicChatAssistantStream.init: qwenModel={}, deepseekModel={}", 
            qwenModel != null ? "not null" : "null",
            deepseekModel != null ? "not null" : "null");

        if (qwenModel != null) {
            qwenAssistant = createChatAssistant(qwenModel);
            log.info("DynamicChatAssistantStream: Qwen流式助手初始化成功");
        } else {
            log.warn("DynamicChatAssistantStream: Qwen流式模型未配置");
        }

        if (deepseekModel != null) {
            deepseekAssistant = createChatAssistant(deepseekModel);
            log.info("DynamicChatAssistantStream: DeepSeek流式助手初始化成功");
        } else {
            log.warn("DynamicChatAssistantStream: DeepSeek流式模型未配置");
        }
    }

    /**
     * 根据当前ModelContext动态选择模型进行流式聊天
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @return 内容块的Flux流
     */
    public Flux<String> chat(Long memoryId, String userMessage) {
        String modelId = ModelContext.getModel();
        log.info("DynamicChatAssistantStream.chat: 获取到ModelContext.getModel()={}, memoryId={}", modelId, memoryId);
        
        if (modelId == null || modelId.isEmpty()) {
            modelId = DEFAULT_MODEL;
            log.info("DynamicChatAssistantStream: 使用默认模型 {}", modelId);
        }

        log.info("DynamicChatAssistantStream准备使用模型: {}, memoryId: {}", modelId, memoryId);

        ChatAssistantStream assistant = getChatAssistant(modelId);
        
        log.info("DynamicChatAssistantStream: 获取到assistant={}, qwenAssistant={}, deepseekAssistant={}", 
            assistant != null ? "not null" : "null",
            qwenAssistant != null ? "not null" : "null",
            deepseekAssistant != null ? "not null" : "null");
        
        if (assistant == null) {
            log.warn("模型 {} 不可用，使用默认Qwen模型", modelId);
            assistant = qwenAssistant;
        }

        if (assistant == null) {
            log.error("没有任何可用的流式模型");
            return Flux.just("抱歉，当前没有可用的流式模型，请检查配置");
        }

        return assistant.chat(memoryId, userMessage);
    }

    /**
     * 根据模型ID获取对应的ChatAssistant
     */
    private ChatAssistantStream getChatAssistant(String modelId) {
        switch (modelId.toLowerCase()) {
            case "deepseek":
                return deepseekAssistant;
            case "qwen":
                return qwenAssistant;
            default:
                log.warn("模型 {} 暂不支持流式切换，使用Qwen", modelId);
                return qwenAssistant;
        }
    }

    /**
     * 创建ChatAssistant
     */
    private ChatAssistantStream createChatAssistant(StreamingChatModel chatModel) {
        var builder = AiServices.builder(ChatAssistantStream.class)
                .streamingChatModel(chatModel);

        if (chatMemoryProvider != null) {
            builder.chatMemoryProvider(chatMemoryProvider);
        }

        if (contentRetriever != null) {
            builder.contentRetriever(contentRetriever);
        }

        return builder.build();
    }

    /**
     * 刷新所有模型助手（当配置变化时调用）
     */
    public void refreshAllModels() {
        init();
    }
}
