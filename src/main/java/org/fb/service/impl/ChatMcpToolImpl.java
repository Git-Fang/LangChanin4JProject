package org.fb.service.impl;

import org.fb.service.ChatService;
import org.fb.service.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class ChatMcpToolImpl implements McpTool {

    @Autowired
    private ChatService chatService;

    @Override
    public String getName() {
        return "composite_intelligent_agent";
    }

    @Override
    public String getDescription() {
        return "复合智能助手，支持本地知识库对话、术语提取、术语增强式翻译、智能医疗助手、数据库操作助手等多种类型功能。";
    }

    @Override
    public Object getInputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("userMessage"),
            "properties", Map.of(
                "memoryId", Map.of(
                    "type", "integer",
                    "description", "对话记忆ID，用于保持上下文连续性",
                    "minimum", 0
                ),
                "userMessage", Map.of(
                    "type", "string",
                    "description", "用户输入的消息",
                    "minLength", 1
                )
            )
        );
    }

    @Override
    public Object getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "chatResult", Map.of(
                    "type", "string",
                    "description", "聊天结果"
                ),
                "type", Map.of(
                    "type", "string",
                    "description", "对话类型",
                    "enum", List.of("普通聊天", "医疗咨询", "翻译", "术语提取", "SQL查询")
                )
            )
        );
    }

    @Override
    public Mono<Object> execute(Object params) {
        Map<String, Object> p = (Map<String, Object>) params;
        String text = (String) p.get("userMessage");
        
        Long memoryId;
        if (p.containsKey("memoryId") && p.get("memoryId") != null) {
            memoryId = Long.parseLong(p.get("memoryId").toString());
        } else {
            // HTTP模式下不使用历史会话，生成临时memoryId
            memoryId = System.currentTimeMillis();
        }

        String result = chatService.chat(memoryId, text);
        return Mono.just(Map.of(
            "chatResult", result,
            "type", "复合智能助手响应"
        ));
    }
}