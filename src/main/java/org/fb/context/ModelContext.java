package org.fb.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模型选择上下文 - 使用ThreadLocal存储当前请求的模型选择
 * 支持在同一个请求的不同服务之间传递模型选择信息
 */
public class ModelContext {

    private static final Logger log = LoggerFactory.getLogger(ModelContext.class);

    /**
     * 请求头中的模型选择Header名称
     */
    public static final String MODEL_HEADER = "X-Selected-Model";

    /**
     * Session中的模型选择Key
     */
    public static final String MODEL_SESSION_KEY = "selectedModel";

    private static final ThreadLocal<String> currentModel = new ThreadLocal<>();
    private static final ThreadLocal<String> originalModel = new ThreadLocal<>();

    /**
     * 设置当前请求使用的模型
     * @param modelId 模型ID
     */
    public static void setModel(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            clearModel();
            return;
        }
        String trimmedModel = modelId.trim();
        log.debug("设置当前模型: {}", trimmedModel);
        currentModel.set(trimmedModel);
    }

    /**
     * 获取当前请求使用的模型
     * @return 模型ID，如果未设置则返回null
     */
    public static String getModel() {
        return currentModel.get();
    }

    /**
     * 获取当前模型，如果未设置则返回默认值
     * @param defaultModel 默认模型ID
     * @return 模型ID
     */
    public static String getModelOrDefault(String defaultModel) {
        String model = currentModel.get();
        return (model == null || model.isEmpty()) ? defaultModel : model;
    }

    /**
     * 保存原始模型选择（用于嵌套调用恢复）
     */
    public static void saveOriginalModel() {
        originalModel.set(currentModel.get());
    }

    /**
     * 恢复原始模型选择
     */
    public static void restoreOriginalModel() {
        String original = originalModel.get();
        if (original != null) {
            currentModel.set(original);
        } else {
            clearModel();
        }
        originalModel.remove();
    }

    /**
     * 清除当前模型设置
     */
    public static void clearModel() {
        currentModel.remove();
    }

    /**
     * 检查是否设置了模型
     * @return 是否设置了模型
     */
    public static boolean hasModel() {
        return currentModel.get() != null && !currentModel.get().isEmpty();
    }

    /**
     * 获取当前模型的调试信息
     * @return 调试字符串
     */
    public static String getDebugInfo() {
        String model = currentModel.get();
        return model != null ? model : "(未设置)";
    }
}
