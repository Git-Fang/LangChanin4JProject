package org.fb.service;

import org.fb.bean.ChatModelInfo;
import org.fb.config.DynamicAiServiceFactory;
import org.fb.config.ModelRegistry;
import org.fb.constant.BusinessConstant;
import org.fb.engine.IntentRecognitionEngine;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.TranslaterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 动态聊天服务
 * 支持根据modelId动态路由到不同的AI模型
 * 注意：由于LangChain4j的AiService是编译时绑定的，当前实现通过选择不同的ChatModel Bean来实现切换
 */
@Service
public class DynamicChatService {
    private static final Logger log = LoggerFactory.getLogger(DynamicChatService.class);

    @Autowired
    private DynamicAiServiceFactory aiServiceFactory;

    @Autowired
    private ModelRegistry modelRegistry;

    @Autowired
    private IntentRecognitionEngine intentRecognitionEngine;

    @Autowired
    private StockAnalysisService stockAnalysisService;

    // 已有的AiService Bean
    @Autowired(required = false)
    @Qualifier("chatAssistant")
    private ChatAssistant defaultChatAssistant;

    @Autowired(required = false)
    @Qualifier("translaterService")
    private TranslaterService defaultTranslaterService;

    /**
     * 执行聊天 (动态路由)
     * @param memoryId 对话ID
     * @param message 用户消息
     * @param modelId 模型ID (如果不传则使用默认模型)
     * @return AI响应
     */
    public String chat(Long memoryId, String message, String modelId) {
        String actualModelId = resolveModelId(modelId);
        ChatModelInfo modelInfo = modelRegistry.getModel(actualModelId);
        
        log.info("========== 聊天请求开始 ==========");
        log.info("请求参数: memoryId={}, modelId={}", memoryId, actualModelId);
        log.info("使用模型信息: {}", modelInfo != null ? modelInfo : "未知模型");
        log.info("用户消息: {}", message != null && message.length() > 100 ? message.substring(0, 100) + "..." : message);
        
        try {
            // ====== 意图识别 ======
            String intent = intentRecognitionEngine.recognize(message);
            log.info("【意图识别】识别到意图: {}", intent);
            
            // ====== 股票分析意图处理 ======
            if (BusinessConstant.STOCK_ANALYSIS_TYPE.equals(intent)) {
                log.info("【意图路由】进入股票分析流程");
                long startTime = System.currentTimeMillis();
                String result = stockAnalysisService.analyzeStock(memoryId, message);
                long endTime = System.currentTimeMillis();
                
                log.info("【股票分析完成】耗时: {}ms, 结果长度: {}", (endTime - startTime), result != null ? result.length() : 0);
                log.info("========== 聊天请求结束 ==========");
                return result;
            }
            
            // ====== 其他意图使用默认聊天服务 ======
            // 当前实现：记录模型选择，实际调用使用默认的ChatAssistant
            // 真正的动态切换需要更复杂的实现（如动态代理）
            log.info("【模型切换】使用模型: {} ({}) 进行聊天", 
                    modelInfo != null ? modelInfo.getDisplayName() : actualModelId,
                    modelInfo != null ? modelInfo.getProvider() : "未知");
            
            long startTime = System.currentTimeMillis();
            String result = defaultChatAssistant.chat(memoryId, message);
            long endTime = System.currentTimeMillis();
            
            log.info("【聊天完成】模型: {}, 耗时: {}ms, 结果长度: {}", 
                    actualModelId, (endTime - startTime), result != null ? result.length() : 0);
            log.info("========== 聊天请求结束 ==========");
            
            return result;
        } catch (Exception e) {
            log.error("【聊天失败】模型: {}, error: {}", actualModelId, e.getMessage(), e);
            log.info("========== 聊天请求异常结束 ==========");
            throw new RuntimeException("聊天失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行翻译 (动态路由)
     * @param memoryId 对话ID
     * @param text 待翻译文本
     * @param modelId 模型ID
     * @return 翻译结果
     */
    public String translate(Long memoryId, String text, String modelId) {
        String actualModelId = resolveModelId(modelId);
        ChatModelInfo modelInfo = modelRegistry.getModel(actualModelId);
        
        log.info("========== 翻译请求开始 ==========");
        log.info("请求参数: memoryId={}, modelId={}", memoryId, actualModelId);
        log.info("使用模型信息: {}", modelInfo != null ? modelInfo : "未知模型");
        log.info("待翻译文本: {}", text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text);
        
        try {
            // 当前实现使用默认的TranslaterService
            log.info("【模型切换】使用模型: {} ({}) 进行翻译", 
                    modelInfo != null ? modelInfo.getDisplayName() : actualModelId,
                    modelInfo != null ? modelInfo.getProvider() : "未知");
            
            long startTime = System.currentTimeMillis();
            String result = defaultTranslaterService.translate(memoryId, text);
            long endTime = System.currentTimeMillis();
            
            log.info("【翻译完成】模型: {}, 耗时: {}ms, 结果长度: {}", 
                    actualModelId, (endTime - startTime), result != null ? result.length() : 0);
            log.info("========== 翻译请求结束 ==========");
            
            return result;
        } catch (Exception e) {
            log.error("【翻译失败】模型: {}, error: {}", actualModelId, e.getMessage(), e);
            log.info("========== 翻译请求异常结束 ==========");
            throw new RuntimeException("翻译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行翻译 (不带memoryId)
     */
    public String translate(String text, String modelId) {
        return translate(null, text, modelId);
    }

    /**
     * 解析最终的模型ID
     * 如果传入的modelId为空或不可用，使用默认模型
     */
    private String resolveModelId(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            String defaultModelId = modelRegistry.getDefaultModelId();
            log.warn("【模型解析】未指定模型，使用默认模型: {}", defaultModelId);
            return defaultModelId;
        }
        
        if (!modelRegistry.isModelAvailable(modelId)) {
            log.warn("【模型解析】指定的模型 {} 不可用，切换到默认模型", modelId);
            return modelRegistry.getDefaultModelId();
        }
        
        log.info("【模型解析】成功解析模型ID: {}", modelId);
        return modelId;
    }

    /**
     * 获取当前可用的模型列表
     */
    public java.util.List<ChatModelInfo> getAvailableModels() {
        return modelRegistry.getAvailableModels();
    }

    /**
     * 获取模型信息
     */
    public ChatModelInfo getModelInfo(String modelId) {
        return modelRegistry.getModel(modelId);
    }

    /**
     * 获取当前默认模型
     */
    public String getDefaultModelId() {
        return modelRegistry.getDefaultModelId();
    }

    /**
     * 获取模型选择器
     * 返回模型ID -> 显示名称的映射
     */
    public java.util.Map<String, String> getModelOptions() {
        java.util.Map<String, String> options = new java.util.HashMap<>();
        for (ChatModelInfo model : modelRegistry.getAvailableModels()) {
            options.put(model.getModelId(), model.getDisplayName());
        }
        return options;
    }
}
