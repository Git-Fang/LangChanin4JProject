package org.fb.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.fb.config.LLMConfig;
import org.fb.context.ModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 模型路由器 - 根据ModelContext动态选择ChatModel
 * 支持在运行时切换不同的大模型
 */
@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private static final String DEFAULT_MODEL = "qwen";

    @Autowired
    private LLMConfig llmConfig;

    /**
     * 获取当前请求应该使用的ChatModel
     * @return ChatModel实例
     */
    public ChatModel getChatModel() {
        String modelId = ModelContext.getModel();
        if (modelId == null || modelId.isEmpty()) {
            modelId = DEFAULT_MODEL;
        }
        log.debug("路由到模型: {}", modelId);
        return llmConfig.getChatModel(modelId);
    }

    /**
     * 获取当前请求应该使用的StreamingChatModel
     * @return StreamingChatModel实例
     */
    public StreamingChatModel getStreamingChatModel() {
        // Streaming目前只支持Qwen，暂不实现动态切换
        return llmConfig.streamingChatModel();
    }

    /**
     * 获取当前模型ID
     * @return 模型ID
     */
    public String getCurrentModelId() {
        String modelId = ModelContext.getModel();
        return modelId != null ? modelId : DEFAULT_MODEL;
    }

    /**
     * 检查指定模型是否可用
     * @param modelId 模型ID
     * @return 是否可用
     */
    public boolean isModelAvailable(String modelId) {
        return llmConfig.isModelAvailable(modelId);
    }
}
