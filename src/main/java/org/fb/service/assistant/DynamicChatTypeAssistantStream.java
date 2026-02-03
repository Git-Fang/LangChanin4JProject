package org.fb.service.assistant;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
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
 * 动态流式意图识别服务
 * 根据ModelContext中设置的模型ID，动态选择对应的StreamingChatModel进行流式意图识别
 */
@Service
public class DynamicChatTypeAssistantStream {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatTypeAssistantStream.class);
    private static final String DEFAULT_MODEL = "qwen";

    @Autowired
    private LLMConfig llmConfig;

    @Autowired(required = false)
    @Qualifier("chatMemoryProvider")
    private ChatMemoryProvider chatMemoryProvider;

    private ChatTypeAssistantStream qwenAssistant;
    private ChatTypeAssistantStream deepseekAssistant;

    @PostConstruct
    public void init() {
        StreamingChatModel qwenModel = llmConfig.getStreamingChatModel("qwen");
        StreamingChatModel deepseekModel = llmConfig.getStreamingChatModel("deepseek");

        if (qwenModel != null) {
            qwenAssistant = createChatTypeAssistant(qwenModel);
            log.info("DynamicChatTypeAssistantStream: Qwen流式意图识别助手初始化成功");
        } else {
            log.warn("DynamicChatTypeAssistantStream: Qwen流式模型未配置");
        }

        if (deepseekModel != null) {
            deepseekAssistant = createChatTypeAssistant(deepseekModel);
            log.info("DynamicChatTypeAssistantStream: DeepSeek流式意图识别助手初始化成功");
        } else {
            log.warn("DynamicChatTypeAssistantStream: DeepSeek流式模型未配置");
        }
    }

    /**
     * 根据当前ModelContext动态选择模型进行流式意图识别
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @return 内容块的Flux流
     */
    public Flux<String> chat(Long memoryId, String userMessage) {
        String modelId = org.fb.context.ModelContext.getModel();
        log.info("DynamicChatTypeAssistantStream.chat: 获取到 ModelContext.getModel()={}, memoryId={}", modelId, memoryId);
        
        if (modelId == null || modelId.isEmpty()) {
            modelId = DEFAULT_MODEL;
            log.info("DynamicChatTypeAssistantStream: 使用默认模型 {}", modelId);
        }

        log.info("DynamicChatTypeAssistantStream准备使用模型: {}, memoryId: {}", modelId, memoryId);

        ChatTypeAssistantStream assistant = getChatAssistant(modelId);
        
        log.info("DynamicChatTypeAssistantStream: 获取到assistant={}, qwenAssistant={}, deepseekAssistant={}", 
            assistant != null ? "not null" : "null",
            qwenAssistant != null ? "not null" : "null",
            deepseekAssistant != null ? "not null" : "null");
        
        if (assistant == null) {
            log.warn("模型 {} 不可用，使用默认Qwen模型", modelId);
            assistant = qwenAssistant;
        }

        if (assistant == null) {
            log.error("没有任何可用的流式意图识别模型");
            return Flux.just("{\"intent\":\"general\"}");
        }

        return assistant.chat(memoryId, userMessage);
    }

    /**
     * 根据模型ID获取对应的ChatTypeAssistantStream
     */
    private ChatTypeAssistantStream getChatAssistant(String modelId) {
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
     * 创建ChatTypeAssistantStream
     */
    private ChatTypeAssistantStream createChatTypeAssistant(StreamingChatModel chatModel) {
        var builder = AiServices.builder(ChatTypeAssistantStream.class)
                .streamingChatModel(chatModel);

        if (chatMemoryProvider != null) {
            builder.chatMemoryProvider(chatMemoryProvider);
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
