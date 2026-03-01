package org.fb.bean;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * AI模型信息类
 * 用于描述可用的AI模型及其配置
 */
public class ChatModelInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 模型唯一标识 (deepseek, qwen, ollama, kimi)
     */
    private String modelId;

    /**
     * 模型实际名称 (deepseek-chat, qwen-max, deepseek-r1:8b, kimi-k2-turbo-preview)
     */
    private String modelName;

    /**
     * 显示名称 (DeepSeek, 通义千问, Ollama本地, Kimi)
     */
    private String displayName;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 模型能力配置
     */
    private Map<String, Object> capabilities;

    /**
     * 模型提供商
     */
    private String provider;

    /**
     * 是否支持流式输出
     */
    private boolean streaming;

    /**
     * 是否支持视觉能力
     */
    private boolean vision;

    /**
     * 是否支持embedding
     */
    private boolean embedding;

    public ChatModelInfo() {
        this.capabilities = new HashMap<>();
    }

    public ChatModelInfo(String modelId, String modelName, String displayName, boolean enabled) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.displayName = displayName;
        this.enabled = enabled;
        this.capabilities = new HashMap<>();
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isVision() {
        return vision;
    }

    public void setVision(boolean vision) {
        this.vision = vision;
    }

    public boolean isEmbedding() {
        return embedding;
    }

    public void setEmbedding(boolean embedding) {
        this.embedding = embedding;
    }

    /**
     * 构建ModelInfo的Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ChatModelInfo info = new ChatModelInfo();

        public Builder modelId(String modelId) {
            info.modelId = modelId;
            return this;
        }

        public Builder modelName(String modelName) {
            info.modelName = modelName;
            return this;
        }

        public Builder displayName(String displayName) {
            info.displayName = displayName;
            return this;
        }

        public Builder enabled(boolean enabled) {
            info.enabled = enabled;
            return this;
        }

        public Builder provider(String provider) {
            info.provider = provider;
            return this;
        }

        public Builder streaming(boolean streaming) {
            info.streaming = streaming;
            info.capabilities.put("streaming", streaming);
            return this;
        }

        public Builder vision(boolean vision) {
            info.vision = vision;
            info.capabilities.put("vision", vision);
            return this;
        }

        public Builder embedding(boolean embedding) {
            info.embedding = embedding;
            info.capabilities.put("embedding", embedding);
            return this;
        }

        public ChatModelInfo build() {
            return info;
        }
    }

    @Override
    public String toString() {
        return "ChatModelInfo{" +
                "modelId='" + modelId + '\'' +
                ", modelName='" + modelName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", enabled=" + enabled +
                ", provider='" + provider + '\'' +
                ", streaming=" + streaming +
                ", vision=" + vision +
                ", embedding=" + embedding +
                '}';
    }
}
