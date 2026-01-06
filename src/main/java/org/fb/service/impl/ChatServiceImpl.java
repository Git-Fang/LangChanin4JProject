package org.fb.service.impl;

import org.fb.constant.BusinessConstant;
import org.fb.service.ChatService;
import org.fb.service.assistant.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private DoctorAgent doctorAgent;

    @Autowired
    private TranslaterService translaterService;

    @Autowired
    private ChatTypeAssistant chatTypeAssistant;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @Autowired
    private NL2SQLService nl2SQLService;

    @Override
    public String chat(Long memoryId, String message) {
        return  processByUserMeanings(memoryId, message);
    }


    /**
     * 使用AI模型理解用户消息意图
     * @param userMessage 用户消息
     * @return 意图分类
     */
    private String processByUserMeanings(Long memoryId, String userMessage) {

        // 为意图识别创建临时memoryId，确保意图识别不受之前会话的影响
        Long tempMemoryId = System.currentTimeMillis();
        
        // 调用AI模型进行意图识别
        String aiResponse = chatTypeAssistant.chat(tempMemoryId, userMessage);
        String lowerResponse = aiResponse.toLowerCase();
        log.info("memoryId：{}；用户意图：{}", memoryId, lowerResponse);

        // 明确识别意图类型，确保只有在明确为医疗意图时才使用医生助手
        boolean isMedical = lowerResponse.contains(BusinessConstant.MEDICAL_TYPE) || lowerResponse.contains("medical") || lowerResponse.contains("医疗");
        boolean isTranslation = lowerResponse.contains(BusinessConstant.TRANSLATION_TYPE) || lowerResponse.contains("translation") || lowerResponse.contains("翻译");
        boolean isTermExtraction = lowerResponse.contains(BusinessConstant.TERM_EXTRACTION_TYPE) || lowerResponse.contains("term_extraction") || lowerResponse.contains("术语");
        boolean isSql = lowerResponse.contains(BusinessConstant.SQL_OPERATION_TYPE) || lowerResponse.contains("sql_transfer") || lowerResponse.contains("sql");
        boolean isGeneral = lowerResponse.contains("general") || lowerResponse.contains("通用") || lowerResponse.contains("普通");

        // 根据解析后的意图，选择不同的业务处理服务
        if (isMedical) {
            // 医疗相关业务，使用医生助手
            return doctorAgent.chat(memoryId, userMessage);
        } else if (isTranslation) {
            // 翻译相关业务，使用翻译服务
            return translaterService.translate(memoryId, userMessage);
        } else if (isTermExtraction) {
            // 术语提取相关业务，使用术语提取助手（不传递memoryId，避免上下文干扰）
            return termExtractionAgent.chat(userMessage);
        } else if (isSql) {
            // 自然语言转为sql
            try {
                List<Map<String, Object>> result = nl2SQLService.executeNaturalLanguageQuery(userMessage);
                if (result == null || result.isEmpty()) {
                    return "查询结果为空，请检查查询条件或数据库中是否有相关数据";
                }
                return formatQueryResult(result);
            } catch (Exception e) {
                log.error("SQL查询执行失败", e);
                
                // 提供更友好的错误提示
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Failed to convert from type")) {
                    return "抱歉，SQL查询时出现类型转换错误。这可能是AI生成的SQL中字段类型不匹配导致的。\n\n" +
                           "建议：\n" +
                           "1. 请尝试更具体地描述您的查询需求\n" +
                           "2. 如果查询涉及数值字段，请明确说明数值范围\n" +
                           "3. 例如：不要说\"查询default用户\"，而要说\"查询ID为1的用户\"\n\n" +
                           "错误详情：" + errorMsg;
                } else if (errorMsg != null && errorMsg.contains("不合理的字符串字面量")) {
                    return "抱歉，AI生成的SQL包含不合理的值。请尝试用不同的方式描述您的查询需求。\n\n" +
                           "建议：\n" +
                           "1. 避免使用\"default\"、\"null\"等关键字作为查询值\n" +
                           "2. 使用具体的数值或文本进行查询\n" +
                           "3. 例如：\"查询用户名为张三的记录\"而不是\"查询default用户\"";
                } else {
                    return "抱歉，执行SQL查询时出错：" + errorMsg + "。\n\n" +
                           "建议：请尝试用更清晰、更具体的方式描述您的查询需求。";
                }
            }
        } else {
            // 默认业务，使用普通聊天助手（个人助手），无论是否明确识别为general
            return chatAssistant.chat(memoryId, userMessage);
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
}
