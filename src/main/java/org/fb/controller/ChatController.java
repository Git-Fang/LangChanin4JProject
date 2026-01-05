package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.Random;

@Tag(name = "智能对话")
@RestController
@RequestMapping("/xiaozhi")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

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


    @PostMapping("/chat2")
    @Operation(summary = "智能对话2")
    public String chat(@RequestBody String userMessage) {

        // 使用AI理解用户意图后执行业务
        return chatService.chat(new Random().nextLong(), userMessage);
    }

}