package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatForm;
import org.fb.service.ChatService;
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
    private ChatService chatService;

    @Operation(summary = "智能对话")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();

        // 使用AI理解用户意图后执行业务
        return chatService.chat(memoryId, userMessage);
    }

}