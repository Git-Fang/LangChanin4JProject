package org.fb.service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatModelInfo;
import org.fb.config.DynamicStreamingChatModel;
import org.fb.config.ModelRegistry;
import org.fb.constant.BusinessConstant;
import org.fb.engine.IntentRecognitionEngine;
import org.fb.service.assistant.*;
import org.fb.service.impl.NL2SQLService;
import org.fb.tools.QdrantOperationTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式业务分发服务
 * 负责流式对话的意图识别和业务分发
 * 逻辑与ChatServiceImpl.processByUserMeanings()保持一致
 * */
@Slf4j
@Service
public class StreamingDispatchService {

    @Autowired
    private ChatTypeAssistantStream chatTypeAssistantStream;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

    @Autowired
    private DoctorAgent doctorAgent;

    @Autowired
    private TranslaterService translaterService;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @Autowired
    private NL2SQLService nl2SQLService;

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;
    
    @Autowired
    private ChatSaveService chatSaveService;

    @Autowired
    private QdrantOperationTools qdrantOperationTools;

    @Autowired
    private BusinessMetricsService metricsService;

    @Autowired
    private IntentRecognitionEngine intentRecognitionEngine;
    
    @Autowired(required = false)
    private ModelRegistry modelRegistry;
    
    @Autowired(required = false)
    @Qualifier("dynamicStreamingChatModel")
    private DynamicStreamingChatModel dynamicStreamingChatModel;

    /**
     * 流式处理用户消息（不支持模型切换）
     *
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @return 内容块的Flux流
       */
    public Flux<String> chat(Long memoryId, String userMessage) {
        return chat(memoryId, userMessage, null);
    }

    /**
     * 流式处理用户消息（支持动态模型切换）
     * 包含意图识别和业务分发逻辑
     *
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @param modelId 模型ID（可选，用于动态模型切换）
     * @return 内容块的Flux流
       */
    public Flux<String> chat(Long memoryId, String userMessage, String modelId) {
        // 解析模型ID
        String actualModelId = resolveModelId(modelId);
        ChatModelInfo modelInfo = modelRegistry != null ? modelRegistry.getModel(actualModelId) : null;
        
        log.info("========== StreamingDispatchService 开始处理 ==========");
        log.info("memoryId: {}, userMessage: {}, 模型ID: {}", memoryId, userMessage, actualModelId);
        
        // 设置动态模型
        if (dynamicStreamingChatModel != null && actualModelId != null) {
            dynamicStreamingChatModel.setCurrentModel(actualModelId);
            log.info("【动态模型切换】已设置当前模型为: {} ({})", 
                    actualModelId,
                    modelInfo != null ? modelInfo.getDisplayName() : "未知");
        } else if (dynamicStreamingChatModel != null) {
            dynamicStreamingChatModel.setCurrentModel("qwen");
            log.warn("【动态模型切换】未指定模型，使用默认: qwen");
        }
        
        // 记录模型切换日志
        if (modelId != null && !modelId.isEmpty()) {
            log.info("【模型切换】使用模型: {} ({}) 进行流式聊天", 
                    modelInfo != null ? modelInfo.getDisplayName() : actualModelId,
                    modelInfo != null ? modelInfo.getProvider() : "未知");
        }
        
        long overallStartTime = System.currentTimeMillis();

        // 第一步：使用规则引擎进行意图识别
        long intentRecognitionStart = System.currentTimeMillis();
        String intent = intentRecognitionEngine.recognize(userMessage);
        long intentRecognitionDuration = System.currentTimeMillis() - intentRecognitionStart;
        
        log.info("步骤1：规则引擎意图识别完成, intent: {}, 耗时: {}ms", intent, intentRecognitionDuration);
        metricsService.recordIntentRecognitionDuration(memoryId.toString(), intentRecognitionDuration);
        metricsService.recordChatRequestByIntent(memoryId.toString(), intent);

        // 判断是否需要启用 RAG 检索
        boolean shouldRetrieve = intentRecognitionEngine.shouldRetrieve(intent);
        log.info("步骤2：是否需要RAG检索: {}", shouldRetrieve);

        // 第二步：根据意图选择业务处理服务
        log.info("步骤3：根据意图选择业务处理服务, intent: {}", intent);
        log.info("当前使用的服务: {}", getServiceName(intent));

        Flux<String> resultFlux;

        // 保存聊天信息到数据库
        log.info("准备调用chatSaveService.saveChatInfo方法，memoryId：{}，用户消息：{}，聊天类型：{}", memoryId, userMessage, intent);
        long dbStartTime = System.currentTimeMillis();
        chatSaveService.saveChatInfo(memoryId, userMessage, intent);
        long dbDuration = System.currentTimeMillis() - dbStartTime;
        metricsService.recordDatabaseOperationDuration(memoryId.toString(), dbDuration);
        log.info("chatSaveService.saveChatInfo方法调用完成, 耗时: {}ms", dbDuration);

        if (BusinessConstant.RAG_RETRIEVAL_TYPE.equals(intent)) {
            log.info("选择业务处理服务: ChatAssistantStream (启用RAG检索)");
            resultFlux = processWithRAG(memoryId, userMessage, shouldRetrieve);
        } else if (BusinessConstant.MEDICAL_TYPE.equals(intent)) {
            log.info("选择业务处理服务: DoctorAgent");
            resultFlux = processWithDoctorAgent(memoryId, userMessage);
        } else if (BusinessConstant.TRANSLATION_TYPE.equals(intent)) {
            log.info("选择业务处理服务: TranslaterService");
            if (translaterService == null) {
                resultFlux = chatAssistantStream.chat(memoryId, userMessage);
            } else {
                resultFlux = processWithTranslaterService(memoryId, userMessage);
            }
        } else if (BusinessConstant.TERM_EXTRACTION_TYPE.equals(intent)) {
            log.info("选择业务处理服务: TermExtractionAgent");
            resultFlux = processWithTermExtractionAgent(userMessage);
        } else if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
            log.info("选择业务处理服务: NaturalLanguageSQLAgent");
            resultFlux = processWithNaturalLanguageSQLAgent(userMessage);
        } else {
            log.info("选择业务处理服务: ChatAssistantStream (默认-general)");
            resultFlux = chatAssistantStream.chat(memoryId, userMessage);
        }

        log.info("========== StreamingDispatchService 处理完成 ==========");
        return resultFlux
                .doOnComplete(() -> {
                    long overallDuration = System.currentTimeMillis() - overallStartTime;
                    metricsService.recordChatProcessingDuration(memoryId.toString(), overallDuration);
                    log.info("StreamingDispatchService整体处理耗时: {}ms", overallDuration);
                });
    }

    private String getServiceName(String intent) {
        return switch (intent) {
            case "rag_retrieval" -> "ChatAssistantStream (RAG检索)";
            case "medical" -> "DoctorAgent";
            case "translation" -> "TranslaterService";
            case "term_extraction" -> "TermExtractionAgent";
            case "sql_transfer" -> "NaturalLanguageSQLAgent";
            default -> "ChatAssistantStream (general)";
        };
    }

    /**
     * 使用 RAG 方式处理（从知识库检索后回答）
     */
    private Flux<String> processWithRAG(Long memoryId, String userMessage, boolean shouldRetrieve) {
        log.info("调用RAG处理, memoryId: {}, message: {}, shouldRetrieve: {}", memoryId, userMessage, shouldRetrieve);
        long serviceStartTime = System.currentTimeMillis();
        
        try {
            // ChatAssistantStream 已配置 contentRetriever，会自动进行知识库检索
            // 这里直接调用即可
            Flux<String> resultFlux = chatAssistantStream.chat(memoryId, userMessage);
            
            log.info("RAG处理已启动, 耗时: {}ms", System.currentTimeMillis() - serviceStartTime);
            return resultFlux;
        } catch (Exception e) {
            log.error("RAG处理失败", e);
            metricsService.recordError(memoryId.toString(), "rag_error");
            return Flux.just("抱歉，处理您的知识库查询请求时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用医生Agent处理（非流式，转Flux）
     */
    private Flux<String> processWithDoctorAgent(Long memoryId, String userMessage) {
        log.info("调用DoctorAgent.chat, memoryId: {}, message: {}", memoryId, userMessage);
        long serviceStartTime = System.currentTimeMillis();
        try {
            String result = doctorAgent.chat(memoryId, userMessage);
            long serviceDuration = System.currentTimeMillis() - serviceStartTime;
            log.info("DoctorAgent返回结果长度: {}, 耗时: {}ms", result != null ? result.length() : 0, serviceDuration);
            metricsService.recordBusinessServiceDuration(memoryId.toString(), serviceDuration, "doctor_agent");
            
            if (result != null) {
                metricsService.recordChatResponseLength(memoryId.toString(), result.length());
            }
            
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("DoctorAgent处理失败", e);
            metricsService.recordError(memoryId.toString(), "doctor_agent_error");
            return Flux.just("抱歉，处理您的医疗咨询时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用翻译服务处理（非流式，转Flux）
     */
    private Flux<String> processWithTranslaterService(Long memoryId, String userMessage) {
        log.info("调用TranslaterService.translate, memoryId: {}, message: {}", memoryId, userMessage);
        long serviceStartTime = System.currentTimeMillis();
        try {
            // 处理用户输入格式，提取实际需要翻译的文本
            // 用户可能输入类似"翻译成英文：具体文本"的格式
            String actualTextToTranslate = extractTextForTranslation(userMessage);
            log.info("提取的待翻译文本：{}", actualTextToTranslate);
            
            String result = translaterService.translate(memoryId, actualTextToTranslate);
            long serviceDuration = System.currentTimeMillis() - serviceStartTime;
            log.info("TranslaterService返回结果长度: {}, 耗时: {}ms", result != null ? result.length() : 0, serviceDuration);
            metricsService.recordBusinessServiceDuration(memoryId.toString(), serviceDuration, "translator_service");
            
            if (result != null) {
                metricsService.recordChatResponseLength(memoryId.toString(), result.length());
            }
            
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("TranslaterService处理失败", e);
            metricsService.recordError(memoryId.toString(), "translator_service_error");
            return Flux.just("抱歉，翻译处理时出现错误: " + e.getMessage());
        }
    }
    
    /**
     * 从用户输入中提取待翻译的文本
     * 处理如"翻译成英文：具体文本"或"翻译成中文：具体文本"等格式
     * @param userInput 用户原始输入
     * @return 待翻译的实际文本
     */
    private String extractTextForTranslation(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return userInput;
        }
        
        // 正则表达式匹配各种翻译格式
        // 匹配"翻译成[语言]：[文本]"或"翻译[语言]：[文本]"等格式
        Pattern pattern = Pattern.compile("^[^：]*[语英中文日法德韩俄西阿葡][\\u4e00-\\ufffd]*[：:]\\s*(.*)$");
        Matcher matcher = pattern.matcher(userInput.trim());
        
        if (matcher.find()) {
            String extractedText = matcher.group(1).trim();
            if (!extractedText.isEmpty()) {
                return extractedText;
            }
        }
        
        // 如果正则匹配失败，尝试简单的分割方式
        String[] parts = userInput.split("[：:]", 2);
        if (parts.length > 1) {
            String candidate = parts[1].trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        
        // 如果都无法提取，返回原始输入
        return userInput;
    }

    /**
     * 使用术语提取Agent处理（非流式，转Flux）
     */
    private Flux<String> processWithTermExtractionAgent(String userMessage) {
        log.info("调用TermExtractionAgent.chat, message: {}", userMessage);
        Long memoryId = System.currentTimeMillis();
        long serviceStartTime = System.currentTimeMillis();
        try {
            String result = termExtractionAgent.chat(userMessage);
            long serviceDuration = System.currentTimeMillis() - serviceStartTime;
            log.info("TermExtractionAgent返回结果长度: {}, 耗时: {}ms", result != null ? result.length() : 0, serviceDuration);
            metricsService.recordBusinessServiceDuration(memoryId.toString(), serviceDuration, "term_extraction_agent");
            
            log.info("术语提取完成，结果: {}", result);
            
            long vectorStartTime = System.currentTimeMillis();
            qdrantOperationTools.embeddingTermAndSave(result);
            long vectorDuration = System.currentTimeMillis() - vectorStartTime;
            metricsService.recordVectorStoreOperationDuration(memoryId.toString(), vectorDuration);
            log.info("术语向量保存完成, 耗时: {}ms", vectorDuration);
            
            if (result != null) {
                metricsService.recordChatResponseLength(memoryId.toString(), result.length());
            }
            
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("TermExtractionAgent处理失败", e);
            metricsService.recordError(memoryId.toString(), "term_extraction_agent_error");
            return Flux.just("抱歉，术语提取时出现错误: " + e.getMessage());
        }
    }

    /**
     * 使用自然语言SQL Agent处理SQL查询（非流式，转Flux）
     * 使用NL2SQLService执行SQL查询并返回结果
     */
    private Flux<String> processWithNaturalLanguageSQLAgent(String userMessage) {
        log.info("调用NL2SQLService.executeNaturalLanguageQuery, message: {}", userMessage);
        Long memoryId = System.currentTimeMillis();
        long serviceStartTime = System.currentTimeMillis();
        try {
            List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(userMessage);
            long serviceDuration = System.currentTimeMillis() - serviceStartTime;
            String result;
            if (sqlResult == null || sqlResult.isEmpty()) {
                result = "查询结果为空，请检查查询条件或数据库中是否有相关数据";
            } else {
                result = formatQueryResult(sqlResult);
            }
            log.info("NL2SQLService返回结果长度: {}, 耗时: {}ms", result != null ? result.length() : 0, serviceDuration);
            metricsService.recordBusinessServiceDuration(memoryId.toString(), serviceDuration, "nl2sql_agent");
            
            if (result != null) {
                metricsService.recordChatResponseLength(memoryId.toString(), result.length());
            }
            
            return Flux.just(result != null ? result : "");
        } catch (Exception e) {
            log.error("NL2SQLService处理失败", e);
            metricsService.recordError(memoryId.toString(), "nl2sql_agent_error");
            String errorMsg = e.getMessage();
            String friendlyError;
            if (errorMsg != null && errorMsg.contains("Failed to convert from type")) {
                friendlyError = "抱歉，SQL查询时出现类型转换错误。这可能是AI生成的SQL中字段类型不匹配导致的。\n\n" +
                        "建议：\n" +
                        "1. 请尝试更具体地描述您的查询需求\n" +
                        "2. 如果查询涉及数值字段，请明确说明数值范围\n" +
                        "3. 错误详情：" + errorMsg;
            } else if (errorMsg != null && errorMsg.contains("不合理的字符串字面量")) {
                friendlyError = "抱歉，AI生成的SQL包含不合理的值。请尝试用不同的方式描述您的查询需求。\n\n" +
                        "建议：\n" +
                        "1. 避免使用\"default\"、\"null\"等关键字作为查询值\n" +
                        "2. 使用具体的数值或文本进行查询";
            } else {
                friendlyError = "抱歉，执行SQL查询时出错：" + errorMsg + "。\n\n" +
                        "建议：请尝试用更清晰、更具体的方式描述您的查询需求。";
            }
            return Flux.just(friendlyError);
        }
    }

    /**
     * 格式化查询结果
     * @param result 查询结果列表
     * @return 格式化后的字符串
     */
    private String formatQueryResult(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "查询结果为空";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("查询成功，共找到").append(result.size()).append("条记录：\n\n");

        int count = 1;
        for (Map<String, Object> row : result) {
            sb.append("记录").append(count++).append(":\n");
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 从AI返回的响应中提取intent
     */
    private String extractIntent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        // 尝试解析JSON格式的响应
        try {
            Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String intent = matcher.group(1).trim().toLowerCase();
                log.info("从JSON中提取的intent: {}", intent);
                return intent;
            }
        } catch (Exception e) {
            log.warn("解析JSON intent失败", e);
        }

        // 如果JSON解析失败，使用兜底策略
        String lowerResponse = aiResponse.toLowerCase();
        if (lowerResponse.contains(BusinessConstant.MEDICAL_TYPE)) {
            return BusinessConstant.MEDICAL_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TRANSLATION_TYPE)) {
            return BusinessConstant.TRANSLATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TERM_EXTRACTION_TYPE)) {
            return BusinessConstant.TERM_EXTRACTION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.SQL_OPERATION_TYPE)) {
            return BusinessConstant.SQL_OPERATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.DEFAULT_TYPE)) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        return BusinessConstant.DEFAULT_TYPE;
    }
    
    /**
     * 解析最终的模型ID
     * 如果传入的modelId为空或不可用，使用默认模型
     */
    private String resolveModelId(String modelId) {
        if (modelRegistry == null) {
            log.warn("【模型解析】ModelRegistry 未注入，使用默认");
            return null;
        }
        
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
}
