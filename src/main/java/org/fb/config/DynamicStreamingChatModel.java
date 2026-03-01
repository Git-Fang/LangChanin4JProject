package org.fb.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态流式聊天模型代理
 * 根据 modelId 动态选择不同的底层 StreamingChatModel
 * 实现真正的运行时模型切换
 */
public class DynamicStreamingChatModel implements StreamingChatModel {
    private static final Logger log = LoggerFactory.getLogger(DynamicStreamingChatModel.class);

    private final Map<String, StreamingChatModel> models = new ConcurrentHashMap<>();
    private volatile String currentModelId = "qwen"; // 默认模型

    /**
     * 注册模型
     */
    public void registerModel(String modelId, StreamingChatModel model) {
        if (modelId == null || model == null) {
            log.warn("跳过无效模型注册: modelId={}, model={}", modelId, model);
            return;
        }
        models.put(modelId, model);
        log.info("【动态模型】已注册模型: {} -> {}", modelId, model.getClass().getSimpleName());
    }

    /**
     * 设置当前使用的模型ID
     */
    public void setCurrentModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            log.warn("【动态模型】未指定模型ID，使用默认: qwen");
            modelId = "qwen";
        }
        
        if (!models.containsKey(modelId)) {
            log.warn("【动态模型】模型 {} 不存在，切换到默认: qwen", modelId);
            modelId = "qwen";
        }
        
        this.currentModelId = modelId;
        log.info("【动态模型】切换到模型: {}", modelId);
    }

    /**
     * 获取当前模型ID
     */
    public String getCurrentModelId() {
        return currentModelId;
    }

    /**
     * 获取当前使用的实际模型
     */
    private StreamingChatModel getCurrentModel() {
        StreamingChatModel model = models.get(currentModelId);
        if (model == null) {
            log.error("【动态模型】模型 {} 未找到，使用第一个可用模型", currentModelId);
            model = models.values().stream().findFirst().orElse(null);
        }
        return model;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        log.info("【动态模型】chat() 调用, modelId={}", currentModelId);
        StreamingChatModel model = getCurrentModel();
        if (model == null) {
            handler.onError(new IllegalStateException("没有可用的流式聊天模型"));
            return;
        }
        model.chat(chatRequest, handler);
    }

    /**
     * 获取所有已注册的模型
     */
    public Map<String, StreamingChatModel> getModels() {
        return models;
    }

    /**
     * 检查模型是否已注册
     */
    public boolean hasModel(String modelId) {
        return models.containsKey(modelId);
    }
}
