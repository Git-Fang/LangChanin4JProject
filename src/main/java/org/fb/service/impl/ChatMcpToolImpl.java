package org.fb.service.impl;

import org.fb.service.ChatService;
import org.fb.service.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Random;

@Component
public class ChatMcpToolImpl implements McpTool {

    @Autowired
    private ChatService chatService;

    @Override
    public String getName() {
        return "复合智能Agent对话";
    }

    @Override
    public String getDescription() {
        return "支持本地知识库对话、术语提取、术语增强式翻译、智能医疗助手、数据库操作助手等多种类型功能。";
    }

    @Override
    public Object getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("userMessage", Map.of("type", "string"))
        );
    }

    @Override
    public Object getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("chatResult", Map.of("type", "string"))
        );
    }

    @Override
    public Mono<Object> execute(Object params) {
        Map<String, Object> p = (Map<String, Object>) params;
        String text = (String) p.get("userMessage");

        String result = chatService.chat(new Random().nextLong(), text);
        return Mono.just(Map.of("chatResult", result));
    }
}