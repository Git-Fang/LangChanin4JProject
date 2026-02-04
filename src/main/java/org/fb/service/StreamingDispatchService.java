package org.fb.service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.fb.constant.BusinessConstant;
import org.fb.context.TracingContextSnapshot;
import org.fb.service.assistant.*;
import org.fb.service.impl.NL2SQLService;
import org.fb.tools.QdrantOperationTools;
import org.fb.util.AIAPIErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
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
    private DynamicChatTypeAssistantStream dynamicChatTypeAssistantStream;

    @Autowired
    private DynamicChatAssistantStream dynamicChatAssistantStream;

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

    @Autowired(required = false)
    private DynamicKnowledgeBaseAssistantStream knowledgeBaseAssistantStream;

    /**
     * 流式处理用户消息
     * 包含意图识别和业务分发逻辑
     *
     * @param memoryId 会话ID
     * @param userMessage 用户消息
     * @return 内容块的Flux流
       */
    public Flux<String> chat(Long memoryId, String userMessage) {
        log.info("========== StreamingDispatchService 开始处理 ==========");
        log.info("memoryId: {}, userMessage: {}", memoryId, userMessage);
        long overallStartTime = System.currentTimeMillis();

        // 在线程切换前保存当前选择的模型
        String selectedModel = org.fb.context.ModelContext.getModel();
        log.info("StreamingDispatchService 获取到 ModelContext.getModel() = {}", selectedModel);

        // 检查服务是否可用
        if (dynamicChatTypeAssistantStream == null) {
            log.warn("DynamicChatTypeAssistantStream 未配置，使用默认general类型");
            chatSaveService.saveChatInfo(memoryId, userMessage, BusinessConstant.DEFAULT_TYPE);
            return dynamicChatAssistantStream.chat(memoryId, userMessage);
        }

        // 第一步：进行意图识别
        Long tempMemoryId = System.currentTimeMillis();
        log.info("步骤1：开始意图识别, tempMemoryId: {}", tempMemoryId);
        log.info("待识别消息: {}", userMessage);
        long intentRecognitionStart = System.currentTimeMillis();

        // 保存模型ID供后续使用
        final String finalSelectedModel = selectedModel;

        // 在collectList操作前捕获当前trace上下文（解决ForkJoinPool线程中traceId丢失问题）
        final Map<String, String> traceContext = TracingContextSnapshot.capture();

        return TracingContextSnapshot.contextWrite(
                dynamicChatTypeAssistantStream.chat(tempMemoryId, userMessage)
                        .collectList()
                        .flatMapMany(intentChunks -> {
                            // 在flatMap中恢复trace上下文（因为collectList切换到了ForkJoinPool线程）
                            return TracingContextSnapshot.executeWithContext(() -> {
                                // 合并意图识别的结果
                                String intentResponse = String.join("", intentChunks);
                                long intentRecognitionDuration = System.currentTimeMillis() - intentRecognitionStart;
                                
                                log.info("步骤2：意图识别原始响应: {}", intentResponse);
                                metricsService.recordIntentRecognitionDuration(memoryId.toString(), intentRecognitionDuration);

                                // 提取意图
                                String intent = extractIntent(intentResponse);
                                log.info("步骤3：解析出的意图: {}, 原始响应: {}", intent, intentResponse);
                                log.info("当前使用的服务: {}", getServiceName(intent));

                                metricsService.recordChatRequestByIntent(memoryId.toString(), intent);

                                // 第二步：根据意图选择业务处理服务
                                log.info("步骤4：根据意图选择业务处理服务, intent: {}", intent);

                                Flux<String> resultFlux;

                                // 保存聊天信息到数据库
                                log.info("准备调用chatSaveService.saveChatInfo方法，memoryId：{}，用户消息：{}，聊天类型：{}", memoryId, userMessage, intent);
                                long dbStartTime = System.currentTimeMillis();
                                chatSaveService.saveChatInfo(memoryId, userMessage, intent);
                                long dbDuration = System.currentTimeMillis() - dbStartTime;
                                metricsService.recordDatabaseOperationDuration(memoryId.toString(), dbDuration);
                                log.info("chatSaveService.saveChatInfo方法调用完成, 耗时: {}ms", dbDuration);

                                if (BusinessConstant.MEDICAL_TYPE.equals(intent)) {
                                    log.info("选择业务处理服务: DoctorAgent");
                                    resultFlux = processWithDoctorAgent(memoryId, userMessage);
                                } else if (BusinessConstant.TRANSLATION_TYPE.equals(intent)) {
                                    log.info("选择业务处理服务: TranslaterService");
                                    if (translaterService == null) {
                                        resultFlux = dynamicChatAssistantStream.chat(memoryId, userMessage);
                                    } else {
                                        resultFlux = processWithTranslaterService(memoryId, userMessage);
                                    }
                                } else if (BusinessConstant.TERM_EXTRACTION_TYPE.equals(intent)) {
                                    log.info("选择业务处理服务: TermExtractionAgent");
                                    resultFlux = processWithTermExtractionAgent(userMessage);
                                } else if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
                                    log.info("选择业务处理服务: NaturalLanguageSQLAgent");
                                    resultFlux = processWithNaturalLanguageSQLAgent(userMessage);
                                } else if (BusinessConstant.KNOWLEDGE_BASE_TYPE.equals(intent)) {
                                    log.info("选择业务处理服务: KnowledgeBaseAssistant");
                                    resultFlux = processWithKnowledgeBaseAssistant(memoryId, userMessage);
                                } else {
                                    log.info("选择业务处理服务: DynamicChatAssistantStream (默认-general)");
                                    // 如果有选择的模型，使用DynamicChatAssistantStream处理
                                    resultFlux = dynamicChatAssistantStream.chat(memoryId, userMessage);
                                }

                                log.info("========== StreamingDispatchService 处理完成 ==========");
                                return resultFlux;
                            });
                        }),
                traceContext
        )
        .doOnComplete(() -> {
                    long overallDuration = System.currentTimeMillis() - overallStartTime;
                    metricsService.recordChatProcessingDuration(memoryId.toString(), overallDuration);
                    log.info("StreamingDispatchService整体处理耗时: {}ms", overallDuration);
                });
    }

    private String getServiceName(String intent) {
        return switch (intent) {
            case "medical" -> "DoctorAgent";
            case "translation" -> "TranslaterService";
            case "term_extraction" -> "TermExtractionAgent";
            case "sql_transfer" -> "NaturalLanguageSQLAgent";
            case "knowledge_base" -> "KnowledgeBaseAssistant";
            default -> "ChatAssistantStream (general)";
        };
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
        
        // 预判断是否需要查询数据库
        if (!nl2SQLService.shouldQueryDatabase(userMessage)) {
            log.info("NL2SQLService预判断不需要查询数据库，转为general类型处理");
            // 改为使用general处理
            return dynamicChatAssistantStream.chat(memoryId, userMessage);
        }
        
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

            // 使用AI错误处理器解析错误
            String errorMsg = e.getMessage();
            String friendlyError;

            // 使用AI错误处理器解析
            AIAPIErrorHandler.AIErrorResult errorResult = AIAPIErrorHandler.parseError(errorMsg);

            // 检查是否是AI服务相关的错误
            if (errorMsg != null && (errorMsg.contains("AI模型") || errorMsg.contains("AI服务") ||
                errorMsg.contains("输入长度") || errorMsg.contains("超时") ||
                errorMsg.contains("过于频繁") || errorMsg.contains("Range of input length"))) {
                // AI服务错误，直接使用生成的消息
                friendlyError = errorMsg;
            }
            // 检查类型转换错误
            else if (errorMsg != null && errorMsg.contains("Failed to convert from type")) {
                friendlyError = "抱歉，SQL查询时出现类型转换错误。这可能是AI生成的SQL中字段类型不匹配导致的。\n\n" +
                        "建议：\n" +
                        "1. 请尝试更具体地描述您的查询需求\n" +
                        "2. 如果查询涉及数值字段，请明确说明数值范围\n" +
                        "3. 例如：不要说\"查询default用户\"，而要说\"查询ID为1的用户\"\n\n" +
                        "错误详情：" + errorMsg;
            }
            // 检查不合理的字符串字面量错误
            else if (errorMsg != null && errorMsg.contains("不合理的字符串字面量")) {
                friendlyError = "抱歉，AI生成的SQL包含不合理的值。请尝试用不同的方式描述您的查询需求。\n\n" +
                        "建议：\n" +
                        "1. 避免使用\"default\"、\"null\"等关键字作为查询值\n" +
                        "2. 使用具体的数值或文本进行查询\n" +
                        "3. 例如：\"查询用户名为张三的记录\"而不是\"查询default用户\"";
            }
            // 检查空SQL错误
            else if (errorMsg != null && (errorMsg.contains("SQL语句为空") || errorMsg.contains("无法生成SQL"))) {
                friendlyError = "抱歉，AI未能成功生成SQL查询语句。\n\n" +
                        "建议：\n" +
                        "1. 请尝试更清晰地描述您的查询需求\n" +
                        "2. 明确说明要查询的表名和字段\n" +
                        "3. 例如：\"查询appointment表中所有的医生姓名\"";
            }
            // 检查危险操作错误
            else if (errorMsg != null && errorMsg.contains("不允许执行修改数据的SQL操作")) {
                friendlyError = "抱歉，为了数据安全，不允许执行修改数据的SQL操作（如DELETE、UPDATE等）。\n\n" +
                        "当前功能仅支持SELECT查询操作。\n" +
                        "如需执行数据操作，请联系数据库管理员。";
            }
            // 默认错误消息
            else {
                friendlyError = "抱歉，执行SQL查询时出错：" + errorMsg + "。\n\n" +
                        "建议：\n" +
                        "1. 请尝试用更清晰、更具体的方式描述您的查询需求\n" +
                        "2. 简化问题，避免过长的描述\n" +
                        "3. 如果问题持续，请联系管理员";
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
        } else if (lowerResponse.contains(BusinessConstant.KNOWLEDGE_BASE_TYPE)) {
            return BusinessConstant.KNOWLEDGE_BASE_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.DEFAULT_TYPE)) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        return BusinessConstant.DEFAULT_TYPE;
    }

    /**
     * 使用知识库问答助手处理
     */
    private Flux<String> processWithKnowledgeBaseAssistant(Long memoryId, String userMessage) {
        log.info("调用DynamicKnowledgeBaseAssistantStream.chat, memoryId: {}, message: {}", memoryId, userMessage);
        long serviceStartTime = System.currentTimeMillis();

        if (knowledgeBaseAssistantStream == null) {
            log.warn("DynamicKnowledgeBaseAssistantStream 未配置，回退到普通聊天");
            metricsService.recordError(memoryId.toString(), "knowledge_base_assistant_not_configured");
            return dynamicChatAssistantStream.chat(memoryId, userMessage);
        }

        try {
            Flux<String> resultFlux = knowledgeBaseAssistantStream.chat(memoryId, userMessage);
            long serviceDuration = System.currentTimeMillis() - serviceStartTime;
            log.info("DynamicKnowledgeBaseAssistantStream返回结果, 耗时: {}ms", serviceDuration);
            metricsService.recordBusinessServiceDuration(memoryId.toString(), serviceDuration, "knowledge_base_assistant");

            return resultFlux;
        } catch (Exception e) {
            log.error("知识库问答助手处理失败，回退到普通聊天", e);
            metricsService.recordError(memoryId.toString(), "knowledge_base_assistant_error");
            // 回退到普通聊天
            return dynamicChatAssistantStream.chat(memoryId, userMessage);
        }
    }
}
