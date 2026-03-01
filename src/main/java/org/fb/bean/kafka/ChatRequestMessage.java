package org.fb.bean.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("memory_id")
    private Long memoryId;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("callback_url")
    private String callbackUrl;
    
    @JsonProperty("message_type")
    private MessageType messageType;
    
    @JsonProperty("metadata")
    private RequestMetadata metadata;
    
    public enum MessageType {
        CHAT, TRANSLATION, MEDICAL, TERM_EXTRACT, SQL_QUERY, UNKNOWN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestMetadata implements Serializable {
        private static final long serialVersionUID = 1L;
        private String source;
        private Priority priority;
        private int retryCount;
        private String modelId;  // 模型ID，用于动态模型切换
    }
    
    public enum Priority { LOW, NORMAL, HIGH, VIP }
    
    public static ChatRequestMessage create(Long memoryId, String message) {
        return ChatRequestMessage.builder()
                .requestId(UUID.randomUUID().toString())
                .memoryId(memoryId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .messageType(MessageType.CHAT)
                .metadata(RequestMetadata.builder()
                        .source("api")
                        .priority(Priority.NORMAL)
                        .retryCount(0)
                        .build())
                .build();
    }
    
    /**
     * 创建带模型ID的请求消息
     * @param memoryId 会话ID
     * @param message 消息内容
     * @param modelId 模型ID（可选，用于动态模型切换）
     */
    public static ChatRequestMessage create(Long memoryId, String message, String modelId) {
        return ChatRequestMessage.builder()
                .requestId(UUID.randomUUID().toString())
                .memoryId(memoryId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .messageType(MessageType.CHAT)
                .metadata(RequestMetadata.builder()
                        .source("api")
                        .priority(Priority.NORMAL)
                        .retryCount(0)
                        .modelId(modelId)
                        .build())
                .build();
    }
}
