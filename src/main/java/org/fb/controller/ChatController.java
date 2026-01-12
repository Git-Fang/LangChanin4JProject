package org.fb.controller;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.bean.MessageDTO;
import org.fb.service.ChatService;
import org.fb.tools.MongoChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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

        log.info("收到聊天请求，memoryId：{}，用户消息：{}", memoryId, userMessage);
        try {
            // 使用AI理解用户意图后执行业务
            System.out.println("\n=== ChatController.chat 开始调用 chatService.chat ===");
            System.out.println("memoryId：" + memoryId);
            System.out.println("userMessage：" + userMessage);
            String result = chatService.chat(memoryId, userMessage);
            System.out.println("chatService.chat 返回结果：" + result);
            System.out.println("=== ChatController.chat 调用 chatService.chat 完成 ===\n");
            return result;
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
            List<String> stringIds = mongoChatMemoryStore.getAllMemoryIds();
            List<Long> longIds = new ArrayList<>();
            for (String id : stringIds) {
                try {
                    longIds.add(Long.parseLong(id));
                } catch (NumberFormatException e) {
                    // 忽略非数字的memoryId（如"default"等系统内部使用的值）
                    log.debug("跳过非数字memoryId: {}", id);
                }
            }
            return longIds;
        } catch (Exception e) {
            log.error("获取历史会话列表异常, error={}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @GetMapping("/history/{memoryId}")
    @Operation(summary = "获取指定会话的历史消息")
    public List<MessageDTO> getHistoryMessages(@PathVariable Long memoryId) {
        try {
            List<ChatMessage> messages = mongoChatMemoryStore.getMessages(memoryId);
            return messages.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取历史消息异常, memoryId={}, error={}", memoryId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 将ChatMessage转换为MessageDTO
     */
    private MessageDTO convertToDTO(ChatMessage message) {
        String type;
        String text;

        if (message instanceof UserMessage) {
            type = "USER_MESSAGE";
            text = ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            type = "AI_MESSAGE";
            text = ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            type = "SYSTEM_MESSAGE";
            text = ((SystemMessage) message).text();
        } else {
            type = "UNKNOWN";
            text = message.toString();
        }

        return new MessageDTO(type, text);
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