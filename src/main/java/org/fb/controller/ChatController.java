package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatForm;
import org.fb.constant.BusinessConstant;
import org.fb.service.assistant.*;
import org.fb.service.impl.NL2SQLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "智能对话")
@RestController
@RequestMapping("/xiaozhi")
@Slf4j
public class ChatController {

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

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

    @Operation(summary = "智能对话")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();

        // 使用AI理解用户意图
        String intent = determineIntentWithAI(memoryId, userMessage);
        log.info("memoryId：{}；用户意图：{}。", memoryId, intent);

        // 根据用户消息内容选择不同的业务处理服务
        if (intent.equalsIgnoreCase(BusinessConstant.MEDICAL_TYPE)) {
            // 医疗相关业务，使用医生助手
            return doctorAgent.chat(memoryId, userMessage);
        } else if (intent.equalsIgnoreCase(BusinessConstant.TRANSLATION_TYPE)) {
            // 翻译相关业务，使用翻译服务
            return translaterService.translate(memoryId, userMessage);
        }  else if (intent.equalsIgnoreCase(BusinessConstant.SQL_OPERATION_TYPE)) {
            // 自然语言转为sql
            return nl2SQLService.executeNaturalLanguageQuery(userMessage).toString();
        } else if (intent.equalsIgnoreCase(BusinessConstant.TERM_EXTRACTION_TYPE)) {
            // 术语提取相关业务，使用术语提取助手
            return termExtractionAgent.chat(memoryId, userMessage);
        } else {
            // 默认业务，使用普通聊天助手
            return chatAssistant.chat(memoryId, userMessage);
        }
    }

    /**
     * 检查消息中是否包含指定关键词
     * @param message 消息内容
     * @param keywords 关键词数组
     * @return 是否包含关键词
     */
    private boolean containsKeywords(String message, String... keywords) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用AI模型理解用户消息意图
     * @param message 用户消息
     * @return 意图分类
     */
    private String determineIntentWithAI(Long memoryId, String message) {

        // 调用AI模型进行意图识别
        String aiResponse = chatTypeAssistant.chat(memoryId, message);

        // 简单解析AI响应中的意图
        String lowerResponse = aiResponse.toLowerCase();
        if (lowerResponse.contains("medical") || lowerResponse.contains("医疗")) {
            return BusinessConstant.MEDICAL_TYPE;
        } else if (lowerResponse.contains("translation") || lowerResponse.contains("翻译")) {
            return BusinessConstant.TRANSLATION_TYPE;
        } else if (lowerResponse.contains("term_extraction") || lowerResponse.contains("术语")) {
            return BusinessConstant.TERM_EXTRACTION_TYPE;
        } else if (lowerResponse.contains("sql_transfer") || lowerResponse.contains("sql")) {
            return BusinessConstant.SQL_OPERATION_TYPE;
        } else {
            return BusinessConstant.DEFAULT_TYPE;
        }
    }

}