package org.fb.bean;

/**
 * 大模型信息DTO
 */
public class ModelInfo {

    /**
     * 模型标识符（如：deepseek, qwen, kimi, ollama）
     */
    private String id;

    /**
     * 模型显示名称
     */
    private String name;

    /**
     * 模型类型（如：deepseek-chat, qwen-max, kimi-k2-turbo-preview）
     */
    private String model;

    /**
     * 模型基础URL
     */
    private String baseUrl;

    /**
     * 模型是否可用（已配置且可正常工作）
     */
    private boolean available;

    /**
     * 是否为默认模型
     */
    private boolean defaultModel;

    /**
     * 模型描述
     */
    private String description;

    public ModelInfo() {
    }

    public ModelInfo(String id, String name, String model, String baseUrl, boolean available, boolean defaultModel, String description) {
        this.id = id;
        this.name = name;
        this.model = model;
        this.baseUrl = baseUrl;
        this.available = available;
        this.defaultModel = defaultModel;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(boolean defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 静态工厂方法 - 创建DeepSeek模型信息
     */
    public static ModelInfo createDeepSeek(String model, String baseUrl, boolean available) {
        return new ModelInfo("deepseek", "DeepSeek", model, baseUrl, available, false,
                available ? "DeepSeek Chat 模型" : "API Key 未配置");
    }

    /**
     * 静态工厂方法 - 创建Qwen模型信息
     */
    public static ModelInfo createQwen(String model, String baseUrl, boolean available) {
        return new ModelInfo("qwen", "通义千问", model, baseUrl, available, true,
                available ? "阿里通义千问模型" : "API Key 未配置");
    }

    /**
     * 静态工厂方法 - 创建Kimi模型信息
     */
    public static ModelInfo createKimi(String model, String baseUrl, boolean available) {
        return new ModelInfo("kimi", "Kimi", model, baseUrl, available, false,
                available ? "月之暗面 Kimi 模型" : "API Key 未配置");
    }

    /**
     * 静态工厂方法 - 创建Ollama模型信息
     */
    public static ModelInfo createOllama(String model, String baseUrl, boolean available) {
        return new ModelInfo("ollama", "Ollama", model, baseUrl, available, false,
                available ? "本地 Ollama 模型" : "服务未启动");
    }
}
