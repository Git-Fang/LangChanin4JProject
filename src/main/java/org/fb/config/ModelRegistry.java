package org.fb.config;

import org.fb.bean.ChatModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 模型注册表
 * 管理所有可用的AI模型，支持运行时动态注册/注销
 */
@Component
@RefreshScope
public class ModelRegistry {
    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private final Map<String, ChatModelInfo> models = new ConcurrentHashMap<>();

    @Value("${ai.deepSeek.apiKey:}")
    private String deepSeekApiKey;

    @Value("${ai.dashscope.apiKey:demo}")
    private String dashscopeApiKey;

    @Value("${ai.kimi.base-url:https://api.moonshot.cn/v1}")
    private String kimiBaseUrl;

    @Value("${ai.kimi.apiKey:}")
    private String kimiApiKey;

    @Value("${ai.ollama.model:deepseek-r1:8b}")
    private String ollamaModel;

    @Autowired(required = false)
    private LLMConfig llmConfig;

    @PostConstruct
    public void init() {
        refreshModels();
    }

    /**
     * 初始化/刷新所有模型
     */
    public void refreshModels() {
        log.info("==================== 模型注册表开始刷新 ====================");
        log.info("当前配置检查: deepSeekApiKey={}, dashscopeApiKey={}, kimiApiKey={}, kimiBaseUrl={}", 
                deepSeekApiKey != null ? (deepSeekApiKey.isEmpty() ? "空" : "已配置") : "未配置",
                dashscopeApiKey != null ? (dashscopeApiKey.isEmpty() ? "空" : dashscopeApiKey.equals("demo") ? "demo" : "已配置") : "未配置",
                kimiApiKey != null ? (kimiApiKey.isEmpty() ? "空" : "已配置") : "未配置",
                kimiBaseUrl);
        
        // DeepSeek 模型
        registerModel(ChatModelInfo.builder()
                .modelId("deepseek")
                .modelName("deepseek-chat")
                .displayName("DeepSeek")
                .provider("DeepSeek")
                .enabled(isDeepSeekEnabled())
                .streaming(true)
                .vision(false)
                .embedding(false)
                .build());

        // 通义千问模型
        registerModel(ChatModelInfo.builder()
                .modelId("qwen")
                .modelName("qwen-max")
                .displayName("通义千问")
                .provider("阿里云")
                .enabled(isQwenEnabled())
                .streaming(true)
                .vision(true)
                .embedding(false)
                .build());

        // Ollama 本地模型
        registerModel(ChatModelInfo.builder()
                .modelId("ollama")
                .modelName(ollamaModel)
                .displayName("Ollama (本地)")
                .provider("Ollama")
                .enabled(true)
                .streaming(true)
                .vision(false)
                .embedding(false)
                .build());

        // Kimi 模型
        registerModel(ChatModelInfo.builder()
                .modelId("kimi")
                .modelName("kimi-k2-turbo-preview")
                .displayName("Kimi")
                .provider("月之暗面")
                .enabled(isKimiEnabled())
                .streaming(true)
                .vision(false)
                .embedding(false)
                .build());

        log.info("【模型注册完成】共注册 {} 个模型", models.size());
        log.info("【可用模型】: {}", getAvailableModelIds());
        log.info("【默认模型】: {}", getDefaultModelId());
        
        // 详细打印每个模型的信息
        log.info("==================== 模型详细信息 ====================");
        for (ChatModelInfo model : models.values()) {
            log.info("模型: {} | 显示名: {} | 提供商: {} | 启用: {} | 流式: {} | 视觉: {}", 
                    model.getModelId(), 
                    model.getDisplayName(), 
                    model.getProvider(),
                    model.isEnabled(),
                    model.isStreaming(),
                    model.isVision());
        }
        log.info("==================== 模型注册表刷新完成 ====================");
    }

    /**
     * 检查 DeepSeek 是否可用
     */
    private boolean isDeepSeekEnabled() {
        return deepSeekApiKey != null && !deepSeekApiKey.isEmpty();
    }

    /**
     * 检查通义千问是否可用
     */
    private boolean isQwenEnabled() {
        return dashscopeApiKey != null && !dashscopeApiKey.isEmpty() && !dashscopeApiKey.equals("demo");
    }

    /**
     * 检查 Kimi 是否可用
     */
    private boolean isKimiEnabled() {
        // Kimi 需要API Key和baseUrl都有有效配置
        return kimiApiKey != null && !kimiApiKey.isEmpty() 
                && kimiBaseUrl != null && !kimiBaseUrl.isEmpty();
    }

    /**
     * 注册模型
     */
    public void registerModel(ChatModelInfo modelInfo) {
        if (modelInfo == null || modelInfo.getModelId() == null) {
            log.warn("【模型注册】跳过无效模型: {}", modelInfo);
            return;
        }
        models.put(modelInfo.getModelId(), modelInfo);
        log.info("【模型注册】成功注册模型: {} (显示名: {}, 提供商: {}, 启用状态: {})", 
                modelInfo.getModelId(), 
                modelInfo.getDisplayName(), 
                modelInfo.getProvider(),
                modelInfo.isEnabled());
    }

    /**
     * 注销模型
     */
    public void unregisterModel(String modelId) {
        ChatModelInfo removed = models.remove(modelId);
        if (removed != null) {
            log.info("模型已注销: {}", modelId);
        }
    }

    /**
     * 获取所有模型
     */
    public List<ChatModelInfo> getAllModels() {
        return models.values().stream().collect(Collectors.toList());
    }

    /**
     * 获取所有可用的模型
     */
    public List<ChatModelInfo> getAvailableModels() {
        return models.values().stream()
                .filter(ChatModelInfo::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可用的模型ID列表
     */
    public List<String> getAvailableModelIds() {
        return getAvailableModels().stream()
                .map(ChatModelInfo::getModelId)
                .collect(Collectors.toList());
    }

    /**
     * 根据modelId获取模型信息
     */
    public ChatModelInfo getModel(String modelId) {
        return models.get(modelId);
    }

    /**
     * 根据modelId获取模型信息（如果不存在返回默认模型）
     */
    public ChatModelInfo getModelOrDefault(String modelId) {
        ChatModelInfo model = models.get(modelId);
        if (model != null && model.isEnabled()) {
            return model;
        }
        // 返回第一个可用的模型作为默认
        List<ChatModelInfo> available = getAvailableModels();
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * 检查模型是否可用
     */
    public boolean isModelAvailable(String modelId) {
        ChatModelInfo model = models.get(modelId);
        return model != null && model.isEnabled();
    }

    /**
     * 动态启用/禁用模型
     */
    public void setModelEnabled(String modelId, boolean enabled) {
        ChatModelInfo model = models.get(modelId);
        if (model != null) {
            model.setEnabled(enabled);
            log.info("模型 {} 已{}", modelId, enabled ? "启用" : "禁用");
        } else {
            log.warn("模型不存在: {}", modelId);
        }
    }

    /**
     * 获取默认模型ID
     */
    public String getDefaultModelId() {
        List<ChatModelInfo> available = getAvailableModels();
        if (available.isEmpty()) {
            return null;
        }
        // 优先返回 qwen，否则返回第一个可用的
        return available.stream()
                .filter(m -> "qwen".equals(m.getModelId()))
                .findFirst()
                .map(ChatModelInfo::getModelId)
                .orElse(available.get(0).getModelId());
    }
}
