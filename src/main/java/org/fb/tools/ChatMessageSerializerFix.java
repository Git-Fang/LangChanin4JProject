package org.fb.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义消息序列化器，用于解决LangChain4j消息序列化问题
 *
 * 问题背景：
 * LangChain4j 1.5.0中，UserMessage包含List<Content>而不是简单字符串。
 * 当使用默认的ChatMessageSerializer.messagesToJson()序列化消息时，
 * Content对象被保留，这可能导致OpenAI API返回错误：
 * "Invalid type for 'messages.[0].content': expected one of a string or array of objects, but got an object instead."
 *
 * 解决方案：
 * 自定义序列化器确保content字段被正确序列化为字符串或数组格式
 */
public class ChatMessageSerializerFix {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将ChatMessage列表序列化为OpenAI兼容的JSON格式
     * 确保content字段是字符串或数组，而不是对象
     *
     * @param messages ChatMessage列表
     * @return JSON字符串
     */
    public static String messagesToJson(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }

        ArrayNode jsonArray = objectMapper.createArrayNode();

        for (ChatMessage message : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", toRole(message));

            // 根据消息类型处理content字段
            ObjectNode contentNode = objectMapper.createObjectNode();
            ArrayNode contentArray = objectMapper.createArrayNode();

            String textContent = extractTextContent(message);

            if (textContent != null && !textContent.isEmpty()) {
                // 创建符合OpenAI格式的content数组
                ObjectNode textBlock = objectMapper.createObjectNode();
                textBlock.put("type", "text");
                textBlock.put("text", textContent);
                contentArray.add(textBlock);
            }

            // 如果没有content，添加空字符串作为content
            if (contentArray.size() == 0 && textContent != null) {
                messageNode.put("content", textContent);
            } else if (contentArray.size() > 0) {
                messageNode.set("content", contentArray);
            } else {
                messageNode.put("content", "");
            }

            jsonArray.add(messageNode);
        }

        try {
            return objectMapper.writeValueAsString(jsonArray);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize chat messages", e);
        }
    }

    /**
     * 从ChatMessage中提取文本内容
     *
     * @param message ChatMessage
     * @return 文本内容
     */
    private static String extractTextContent(ChatMessage message) {
        if (message == null) {
            return null;
        }

        if (message instanceof UserMessage userMessage) {
            StringBuilder sb = new StringBuilder();
            List<Content> contents = userMessage.contents();

            if (contents == null || contents.isEmpty()) {
                return userMessage.singleText();
            }

            for (Content content : contents) {
                if (content instanceof TextContent textContent) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(textContent.text());
                }
                // 忽略非文本内容（如图片等）
            }

            return sb.length() > 0 ? sb.toString() : userMessage.singleText();
        }

        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }

        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }

        if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        }

        return message.toString();
    }

    /**
     * 将ChatMessage转换为OpenAI角色字符串
     *
     * @param message ChatMessage
     * @return 角色字符串
     */
    private static String toRole(ChatMessage message) {
        if (message instanceof UserMessage) {
            return "user";
        }
        if (message instanceof AiMessage) {
            return "assistant";
        }
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof ToolExecutionResultMessage) {
            return "tool";
        }
        return "user";
    }

    /**
     * 将OpenAI格式的JSON反序列化为ChatMessage列表
     * 注意：此方法将OpenAI格式转换回LangChain4j格式
     *
     * @param json JSON字符串
     * @return ChatMessage列表
     */
    public static List<ChatMessage> messagesFromJson(String json) {
        List<ChatMessage> messages = new ArrayList<>();

        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return messages;
        }

        try {
            ArrayNode arrayNode = (ArrayNode) objectMapper.readTree(json);

            for (int i = 0; i < arrayNode.size(); i++) {
                ObjectNode messageNode = (ObjectNode) arrayNode.get(i);
                String role = messageNode.has("role") ? messageNode.get("role").asText() : "user";

                // 处理content字段
                String contentText = extractContentFromJson(messageNode);

                switch (role) {
                    case "user":
                        messages.add(UserMessage.from(contentText));
                        break;
                    case "assistant":
                        messages.add(AiMessage.from(contentText));
                        break;
                    case "system":
                        messages.add(SystemMessage.from(contentText));
                        break;
                    case "tool":
                        // ToolExecutionResultMessage需要(id, name, text)三个参数
                        // 由于反序列化时没有原始信息，使用占位符
                        messages.add(ToolExecutionResultMessage.from(
                            "tool_result",
                            "unknown_tool",
                            contentText
                        ));
                        break;
                    default:
                        // 未知角色，作为用户消息处理
                        messages.add(UserMessage.from(contentText));
                        break;
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize chat messages", e);
        }

        return messages;
    }

    /**
     * 从OpenAI格式的JSON中提取content内容
     *
     * @param messageNode 消息节点
     * @return content文本
     */
    private static String extractContentFromJson(ObjectNode messageNode) {
        if (!messageNode.has("content")) {
            return "";
        }

        var contentNode = messageNode.get("content");

        // 如果是字符串，直接返回
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }

        // 如果是数组，遍历提取所有文本内容
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (var item : contentNode) {
                if (item.has("text")) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(item.get("text").asText());
                }
            }
            return sb.toString();
        }

        // 如果是对象，尝试提取text字段
        if (contentNode.isObject() && contentNode.has("text")) {
            return contentNode.get("text").asText();
        }

        return "";
    }
}
