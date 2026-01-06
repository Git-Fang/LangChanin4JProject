package org.fb.service.impl;

import org.fb.constant.BusinessConstant;
import org.fb.service.ChatService;
import org.fb.service.assistant.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
            return nl2SQLService.executeNaturalLanguageQuery(userMessage).toString();
        } else {
            // 默认业务，使用普通聊天助手（个人助手），无论是否明确识别为general
            return chatAssistant.chat(memoryId, userMessage);
        }
    }
}
