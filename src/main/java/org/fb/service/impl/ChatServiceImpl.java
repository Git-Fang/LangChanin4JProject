package org.fb.service.impl;

import org.fb.constant.BusinessConstant;
import org.fb.service.ChatSaveService;
import org.fb.service.ChatService;
import org.fb.service.KnowledgeBaseRetrievalService;
import org.fb.service.assistant.*;
import org.fb.tools.QdrantOperationTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired(required = false)
    private ChatAssistant chatAssistant;

    @Autowired(required = false)
    private DoctorAgent doctorAgent;

    @Autowired(required = false)
    private TranslaterService translaterService;

    @Autowired(required = false)
    private ChatTypeAssistant chatTypeAssistant;

    @Autowired(required = false)
    private TermExtractionAgent termExtractionAgent;

    @Autowired
    private NL2SQLService nl2SQLService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatSaveService chatSaveService;

    @Autowired
    private QdrantOperationTools qdrantOperationTools;

    @Autowired
    private KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;

    @Override
    public String chat(Long memoryId, String message) {
        log.info("\n=== ChatServiceImpl.chat 开始调用 processByUserMeanings ===");
        log.info("memoryId：" + memoryId + "; message：" + message);
        String result = processByUserMeanings(memoryId, message);
        log.info("processByUserMeanings 返回结果：" + result);
        log.info("=== ChatServiceImpl.chat 调用 processByUserMeanings 完成 ===\n");
        return result;
    }


    /**
     * 使用AI模型理解用户消息意图
     * @param memoryId 对话对应的memoryId
     * @param userMessage 用户消息
     * @return 意图分类
     */
    private String processByUserMeanings(Long memoryId, String userMessage) {
        log.info("\n========== processByUserMeanings 方法开始 ==========");
        log.info("memoryId：{}；userMessage：{}", memoryId, userMessage);

        // 检查是否配置了LLM模型
        if (chatTypeAssistant == null || chatAssistant == null) {
            log.warn("未配置 LLM 模型（chatTypeAssistant: {} 或 chatAssistant: {}），使用默认响应",
                    chatTypeAssistant == null ? "null" : "available",
                    chatAssistant == null ? "null" : "available");

            // 保存聊天信息到数据库
            saveChatInfo(memoryId, userMessage, BusinessConstant.DEFAULT_TYPE);

            return "抱歉，聊天服务暂时不可用，请配置 LLM 模型后重试。";
        }

        // 为意图识别创建临时memoryId，确保意图识别不受之前会话的影响
        Long tempMemoryId = System.currentTimeMillis();
        log.info("tempMemoryId：{}", tempMemoryId);

        // 调用AI模型进行意图识别
        log.info("开始调用 chatTypeAssistant.chat 进行意图识别");
        String aiResponse = chatTypeAssistant.chat(tempMemoryId, userMessage);
        log.info("意图识别原始响应：{}", aiResponse);

        String lowerResponse = aiResponse.toLowerCase();
        log.info("aiResponse：{}；lowerResponse：{}", aiResponse, lowerResponse);
        log.info("memoryId：{}；用户意图原始响应：{}", memoryId, aiResponse);

        // 解析AI返回的JSON结果，提取intent字段
        String intent = extractIntent(aiResponse);
        log.info("最终解析出的意图类型：{}", intent);
        
        // 如果解析为 rag_retrieval，优先处理
        if (BusinessConstant.RAG_RETRIEVAL_TYPE.equals(intent)) {
            log.info("识别为 RAG 知识库检索类型，直接调用知识库检索服务");
            String result = knowledgeBaseRetrievalService.searchKnowledgeBase(userMessage);
            saveChatInfo(memoryId, userMessage, BusinessConstant.RAG_RETRIEVAL_TYPE);
            return result;
        }
        
        log.info("========== processByUserMeanings 方法完成 ==========\n");

        // 根据解析出的intent确定聊天类型
        log.info("开始确定聊天类型");
        String chatType = BusinessConstant.DEFAULT_TYPE;
        if (BusinessConstant.MEDICAL_TYPE.equals(intent)) {
            chatType = BusinessConstant.MEDICAL_TYPE;
        } else if (BusinessConstant.TRANSLATION_TYPE.equals(intent)) {
            chatType = BusinessConstant.TRANSLATION_TYPE;
        } else if (BusinessConstant.TERM_EXTRACTION_TYPE.equals(intent)) {
            chatType = BusinessConstant.TERM_EXTRACTION_TYPE;
        } else if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
            chatType = BusinessConstant.SQL_OPERATION_TYPE;
        } else if (BusinessConstant.DEFAULT_TYPE.equals(intent)) {
            chatType = BusinessConstant.DEFAULT_TYPE;
        }
        log.info("聊天类型确定完成：{}", chatType);

        // 根据解析后的意图，选择不同的业务处理服务
        log.info("开始根据意图选择业务处理服务");
        String result;
        if (BusinessConstant.MEDICAL_TYPE.equals(intent) && doctorAgent != null) {
            // 医疗相关业务，使用医生助手
            log.info("选择业务处理服务：DoctorAgent");
            result = doctorAgent.chat(memoryId, userMessage);
            // 保存聊天信息到数据库
            log.info("准备调用saveChatInfo方法，memoryId：" + memoryId + "，用户消息：" + userMessage + "，聊天类型：" + chatType);
            saveChatInfo(memoryId, userMessage, chatType);
            log.info("saveChatInfo方法调用完成");
        } else if (BusinessConstant.TRANSLATION_TYPE.equals(intent) && translaterService != null) {
            // 翻译相关业务，使用翻译服务
            log.info("选择业务处理服务：TranslaterService");
            // 处理用户输入格式，提取实际需要翻译的文本
            // 用户可能输入类似"翻译成英文：具体文本"的格式
            String actualTextToTranslate = extractTextForTranslation(userMessage);
            log.info("提取的待翻译文本：{}", actualTextToTranslate);
            
            result = translaterService.translate(memoryId, actualTextToTranslate);
            
            // 保存聊天信息到数据库
            log.info("准备调用saveChatInfo方法，memoryId：" + memoryId + "，用户消息：" + userMessage + "，聊天类型：" + BusinessConstant.TRANSLATION_TYPE);
            saveChatInfo(memoryId, userMessage, BusinessConstant.TRANSLATION_TYPE);
            log.info("翻译完成，聊天信息已保存");
        } else if (BusinessConstant.TERM_EXTRACTION_TYPE.equals(intent) && termExtractionAgent != null) {
            log.info("选择业务处理服务：TermExtractionAgent");
            result = termExtractionAgent.chat(userMessage);
            
            log.info("准备调用saveChatInfo方法，memoryId：" + memoryId + "，用户消息：" + userMessage + "，聊天类型：" + BusinessConstant.TERM_EXTRACTION_TYPE);
            saveChatInfo(memoryId, userMessage, BusinessConstant.TERM_EXTRACTION_TYPE);
            log.info("术语提取完成，聊天信息已保存");

            qdrantOperationTools.embeddingTermAndSave(result);
            log.info("术语向量保存完成");
        } else if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
            // 自然语言转为sql
            log.info("选择业务处理服务：NL2SQLService");
            try {
                List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(userMessage);
                if (sqlResult == null || sqlResult.isEmpty()) {
                    result = "查询结果为空，请检查查询条件或数据库中是否有相关数据";
                } else {
                    result = formatQueryResult(sqlResult);
                }
            } catch (Exception e) {
                log.error("SQL查询执行失败", e);
                
                // 提供更友好的错误提示
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Failed to convert from type")) {
                    result = "抱歉，SQL查询时出现类型转换错误。这可能是AI生成的SQL中字段类型不匹配导致的。\n\n" +
                           "建议：\n" +
                           "1. 请尝试更具体地描述您的查询需求\n" +
                           "2. 如果查询涉及数值字段，请明确说明数值范围\n" +
                           "3. 例如：不要说\"查询default用户\"，而要说\"查询ID为1的用户\"\n\n" +
                           "错误详情：" + errorMsg;
                } else if (errorMsg != null && errorMsg.contains("不合理的字符串字面量")) {
                    result = "抱歉，AI生成的SQL包含不合理的值。请尝试用不同的方式描述您的查询需求。\n\n" +
                           "建议：\n" +
                           "1. 避免使用\"default\"、\"null\"等关键字作为查询值\n" +
                           "2. 使用具体的数值或文本进行查询\n" +
                           "3. 例如：\"查询用户名为张三的记录\"而不是\"查询default用户\"";
                } else {
                    result = "抱歉，执行SQL查询时出错：" + errorMsg + "。\n\n" +
                           "建议：请尝试用更清晰、更具体的方式描述您的查询需求。";
                }
            }
        } else if (chatAssistant != null) {
            // 默认业务，使用普通聊天助手（个人助手），无论是否明确识别为general
            log.info("选择业务处理服务：ChatAssistant");
            result = chatAssistant.chat(memoryId, userMessage);
        } else {
            // 未配置聊天助手
            result = "抱歉，聊天服务暂时不可用，请配置 LLM 模型后重试。";
        }

        log.info("业务处理服务返回结果：" + result);
        log.info("=== processByUserMeanings 方法完成 ===\n");
        return result;
    }

    /**
     * 从AI返回的JSON结果中提取intent字段的值
     * @param aiResponse AI返回的响应
     * @return intent类型
     */
    private String extractIntent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return BusinessConstant.DEFAULT_TYPE;
        }

        // 尝试解析JSON格式的响应
        try {
            // 匹配 "intent": "xxx" 格式
            Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String intent = matcher.group(1).trim().toLowerCase();
                log.info("从JSON中提取的intent：" + intent);
                return intent;
            }
        } catch (Exception e) {
            log.warn("解析JSON intent失败，返回原始响应", e);
        }

        // 如果JSON解析失败，使用旧的方式进行兼容（兜底策略）
        String lowerResponse = aiResponse.toLowerCase();
        if (lowerResponse.contains(BusinessConstant.RAG_RETRIEVAL_TYPE)) {
            return BusinessConstant.RAG_RETRIEVAL_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.MEDICAL_TYPE)) {
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

        // 默认返回general
        return BusinessConstant.DEFAULT_TYPE;
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
        Pattern pattern = Pattern.compile("^[^：]*[语英中文日法德韩俄西阿葡][\u4e00-\ufffd]*[：:]\s*(.*)$");
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
     * 保存聊天信息到数据库
     * @param memoryId 对话对应的memoryId
     * @param userMessage 用户消息
     * @param chatType 聊天类型
     */
    private void saveChatInfo(Long memoryId, String userMessage, String chatType) {
        log.info("\n=== 开始保存聊天信息 ===");
        log.info("memoryId: {}; 聊天类型: {}", memoryId, chatType);
        chatSaveService.saveChatInfo(memoryId, userMessage, chatType);
        log.info("=== 聊天信息保存完成 ===\n");
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
}
