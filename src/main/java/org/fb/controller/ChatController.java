package org.fb.controller;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatForm;
import org.fb.bean.MessageDTO;
import org.fb.bean.ModelInfo;
import org.fb.config.LLMConfig;
import org.fb.context.ModelContext;
import org.fb.service.ChatService;
import org.fb.tools.MongoChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Tag(name = "智能对话", description = "提供同步和异步两种对话方式")
@RestController
@RequestMapping("/xiaozhi")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;

    @Autowired
    private LLMConfig llmConfig;

    @Operation(summary = "智能对话（同步）", description = "传统的同步对话方式，请求后会阻塞等待AI响应(2-5秒)")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        java.util.List<String> extractedTexts = chatForm.getExtractedTexts();
        String selectedModel = chatForm.getModel();

        log.info("收到聊天请求，memoryId：{}，用户消息：{}", memoryId, userMessage);
        if (extractedTexts != null && !extractedTexts.isEmpty()) {
            log.info("附带文件提取内容数量: {}", extractedTexts.size());
        }

        // 设置模型选择（优先使用请求中的模型，其次使用请求头中的模型）
        if (selectedModel != null && !selectedModel.trim().isEmpty()) {
            ModelContext.setModel(selectedModel.trim());
            log.info("使用请求中指定的模型: {}", selectedModel);
        }

        try {
            String fullMessage = buildFullMessage(userMessage, extractedTexts);
            System.out.println("\n=== ChatController.chat 开始调用 chatService.chat ===");
            System.out.println("memoryId：" + memoryId);
            System.out.println("userMessage：" + fullMessage);
            System.out.println("model：" + ModelContext.getDebugInfo());
            String result = chatService.chat(memoryId, fullMessage);
            System.out.println("chatService.chat 返回结果：" + result);
            System.out.println("=== ChatController.chat 调用 chatService.chat 完成 ===\n");
            return result;
        } catch (Exception e) {
            log.error("对话处理异常, memoryId={}, message={}, error={}", memoryId, userMessage, e.getMessage(), e);
            return "抱歉，处理您的请求时出现了异常，请稍后重试。";
        }
    }

    private String buildFullMessage(String userMessage, java.util.List<String> extractedTexts) {
        if (extractedTexts == null || extractedTexts.isEmpty()) {
            return userMessage;
        }

        StringBuilder fullMessage = new StringBuilder();
        fullMessage.append("用户问题：").append(userMessage).append("\n\n");

        fullMessage.append("附件内容：");
        for (int i = 0; i < extractedTexts.size(); i++) {
            if (i > 0) {
                fullMessage.append("\n\n--- 文件 ").append(i + 1).append(" ---\n");
            }
            fullMessage.append(extractedTexts.get(i));
        }

        return fullMessage.toString();
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

    @PostMapping("/chat/saveHistory")
    @Operation(summary = "保存对话到历史会话")
    public Boolean saveHistory(@RequestBody Map<String, Object> request) {
        try {
            Long memoryId = Long.valueOf(request.get("memoryId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");

            if (messages == null || messages.isEmpty()) {
                log.warn("保存历史会话失败: 消息列表为空, memoryId={}", memoryId);
                return false;
            }

            List<ChatMessage> chatMessages = new ArrayList<>();
            for (Map<String, String> msg : messages) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    chatMessages.add(UserMessage.from(content));
                } else if ("ai".equals(role)) {
                    chatMessages.add(AiMessage.from(content));
                }
            }

            mongoChatMemoryStore.updateMessages(memoryId, chatMessages);
            log.info("保存历史会话成功, memoryId={}, 消息数量={}", memoryId, chatMessages.size());
            return true;
        } catch (Exception e) {
            log.error("保存历史会话异常, error={}", e.getMessage(), e);
            return false;
        }
    }

    @GetMapping("/models")
    @Operation(summary = "获取可用的大模型列表")
    public List<ModelInfo> getAvailableModels() {
        try {
            return llmConfig.getAvailableModels();
        } catch (Exception e) {
            log.error("获取可用模型列表异常, error={}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @PostMapping("/models/select")
    @Operation(summary = "选择大模型")
    public Map<String, Object> selectModel(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String modelId = request.get("model");
            if (modelId == null || modelId.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "模型ID不能为空");
                return result;
            }

            // 检查模型是否可用
            if (!llmConfig.isModelAvailable(modelId)) {
                result.put("success", false);
                result.put("message", "模型不可用，请检查配置");
                return result;
            }

            // 设置模型到ModelContext（通过响应头返回给前端）
            log.info("选择模型: {}", modelId);
            result.put("success", true);
            result.put("message", "模型选择成功");
            result.put("model", modelId);
            return result;
        } catch (Exception e) {
            log.error("选择模型异常, error={}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "选择模型失败: " + e.getMessage());
            return result;
        }
    }

    @PostMapping("/models/refresh")
    @Operation(summary = "刷新模型列表")
    public Map<String, Object> refreshModels() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("刷新模型配置");
            result.put("success", true);
            result.put("message", "模型配置已刷新");
            result.put("models", llmConfig.getAvailableModels());
            return result;
        } catch (Exception e) {
            log.error("刷新模型配置异常, error={}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "刷新模型配置失败: " + e.getMessage());
            return result;
        }
    }

}