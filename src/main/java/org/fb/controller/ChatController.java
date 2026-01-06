package org.fb.controller;

import dev.langchain4j.data.message.ChatMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.service.ChatService;
import org.fb.tools.MongoChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Tag(name = "智能对话")
@RestController
@RequestMapping("/xiaozhi")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Operation(summary = "智能对话")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();

        try {
            // 使用AI理解用户意图后执行业务
            return chatService.chat(memoryId, userMessage);
        } catch (Exception e) {
            log.error("对话处理异常, memoryId={}, message={}, error={}", memoryId, userMessage, e.getMessage(), e);
            return "抱歉，处理您的请求时出现了异常，请稍后重试。";
        }
    }


    @PostMapping("/chat2")
    @Operation(summary = "智能对话2")
    public String chat(@RequestBody String userMessage) {

        try {
            // 使用AI理解用户意图后执行业务
            return chatService.chat(new Random().nextLong(), userMessage);
        } catch (Exception e) {
            log.error("对话2处理异常, message={}, error={}", userMessage, e.getMessage(), e);
            return "抱歉，处理您的请求时出现了异常，请稍后重试。";
        }
    }

    @GetMapping("/history")
    @Operation(summary = "获取历史会话列表")
    public List<Long> getHistorySessions() {
        try {
            return mongoChatMemoryStore.getAllMemoryIds();
        } catch (Exception e) {
            log.error("获取历史会话列表异常, error={}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @GetMapping("/history/{memoryId}")
    @Operation(summary = "获取指定会话的历史消息")
    public List<ChatMessage> getHistoryMessages(@PathVariable Long memoryId) {
        try {
            return mongoChatMemoryStore.getMessages(memoryId);
        } catch (Exception e) {
            log.error("获取历史消息异常, memoryId={}, error={}", memoryId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @DeleteMapping("/history/{memoryId}")
    @Operation(summary = "删除指定会话")
    public Boolean deleteSession(@PathVariable Long memoryId) {
        try {
            mongoChatMemoryStore.deleteMessages(memoryId);
            return true;
        } catch (Exception e) {
            log.error("删除会话异常, memoryId={}, error={}", memoryId, e.getMessage(), e);
            return false;
        }
    }

}