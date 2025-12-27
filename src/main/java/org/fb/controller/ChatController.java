package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.assistant.ChatAssistantStream;
import org.fb.service.assistant.DoctorAgent;
import org.fb.service.assistant.TranslaterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "智能对话")
@RestController
@RequestMapping("/xiaozhi")
public class ChatController {

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private ChatAssistantStream chatAssistantStream;

    @Autowired
    private DoctorAgent doctorAgent;

    @Autowired
    private TranslaterService translaterService;

    @Operation(summary = "智能对话")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();

        // 根据用户消息内容选择不同的业务处理服务
        if (containsKeywords(userMessage, "医院", "挂号", "生病", "医生", "看病", "诊断")) {
            // 医疗相关业务，使用医生助手
            return doctorAgent.chat(memoryId, userMessage);
        } else if (containsKeywords(userMessage, "翻译", "translate", "译", "语言", "英文", "中文")) {
            // 翻译相关业务，使用翻译服务
            return translaterService.translate(memoryId, userMessage);
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

}