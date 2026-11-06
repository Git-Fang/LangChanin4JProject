package org.fb.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态聊天模型代理（非流式）
 * 根据 modelId 动态选择不同的底层 ChatModel，是 DynamicStreamingChatModel 的非流式版本，
 * 用于让 AiService（如 DoctorAgent、翻译、术语提取等）跟随用户在页面上选择的模型。
 */
public class DynamicChatModel implements ChatModel {
    private static final Logger log = LoggerFactory.getLogger(DynamicChatModel.class);

    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private volatile String currentModelId = "deepseek";

    /**
     * 注册模型
     */
    public void registerModel(String modelId, ChatModel model) {
        if (modelId == null || model == null) {
            log.warn("跳过无效模型注册: modelId={}, model={}", modelId, model);
            return;
        }
        models.put(modelId, model);
        log.info("【动态模型-非流式】已注册模型: {} -> {}", modelId, model.getClass().getSimpleName());
    }

    /**
     * 设置当前使用的模型ID
     */
    public void setCurrentModel(String modelId) {
        if (modelId == null || modelId.isEmpty() || !models.containsKey(modelId)) {
            log.warn("【动态模型-非流式】模型 {} 不可用，保持当前模型: {}", modelId, currentModelId);
            return;
        }
        this.currentModelId = modelId;
        log.info("【动态模型-非流式】切换到模型: {}", modelId);
    }

    /**
     * 获取当前模型ID
     */
    public String getCurrentModelId() {
        return currentModelId;
    }

    /**
     * 检查模型是否已注册
     */
    public boolean hasModel(String modelId) {
        return models.containsKey(modelId);
    }

    /**
     * 获取所有已注册的模型
     */
    public Map<String, ChatModel> getModels() {
        return models;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatModel model = models.get(currentModelId);
        if (model == null) {
            log.error("【动态模型-非流式】模型 {} 未找到，使用第一个可用模型", currentModelId);
            model = models.values().stream().findFirst().orElse(null);
        }
        if (model == null) {
            throw new IllegalStateException("没有可用的聊天模型");
        }
        return model.chat(chatRequest);
    }
}