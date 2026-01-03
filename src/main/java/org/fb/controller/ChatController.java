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

        // 使用AI理解用户意图后执行业务
        return  processByUserMeanings(memoryId, userMessage);
    }

    /**
     * 使用AI模型理解用户消息意图
     * @param userMessage 用户消息
     * @return 意图分类
     */
    private String processByUserMeanings(Long memoryId, String userMessage) {

        // 调用AI模型进行意图识别
        String aiResponse = chatTypeAssistant.chat(memoryId, userMessage);
        String lowerResponse = aiResponse.toLowerCase();
        log.info("memoryId：{}；用户意图：{}。", memoryId, lowerResponse);

        // 根据解析后的意图，选择不同的业务处理服务
        if (lowerResponse.contains(BusinessConstant.MEDICAL_TYPE) || lowerResponse.contains("医疗")) {
            // 医疗相关业务，使用医生助手
            return doctorAgent.chat(memoryId, userMessage);
        } else if (lowerResponse.contains(BusinessConstant.TRANSLATION_TYPE) || lowerResponse.contains("翻译")) {
            // 翻译相关业务，使用翻译服务
            return translaterService.translate(memoryId, userMessage);
        } else if (lowerResponse.contains(BusinessConstant.TERM_EXTRACTION_TYPE) || lowerResponse.contains("术语")) {
            // 术语提取相关业务，使用术语提取助手
            return termExtractionAgent.chat(memoryId, userMessage);
        } else if (lowerResponse.contains(BusinessConstant.SQL_OPERATION_TYPE) || lowerResponse.contains("sql")) {
            // 自然语言转为sql
            return nl2SQLService.executeNaturalLanguageQuery(userMessage).toString();
        } else {
            // 默认业务，使用普通聊天助手
            return chatAssistant.chat(memoryId, userMessage);
        }
    }

}