package org.fb.bean.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResultMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("memory_id")
    private Long memoryId;
    
    @JsonProperty("result")
    private String result;
    
    @JsonProperty("status")
    private ResultStatus status;
    
    @JsonProperty("error_message")
    private String errorMessage;
    
    @JsonProperty("processed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime processedAt;
    
    @JsonProperty("processing_time_ms")
    private long processingTimeMs;
    
    @JsonProperty("intent")
    private String intent;
    
    @JsonProperty("token_usage")
    private TokenUsage tokenUsage;
    
    public enum ResultStatus { SUCCESS, PROCESSING, FAILED, TIMEOUT, CANCELLED }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage implements Serializable {
        private static final long serialVersionUID = 1L;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
    
    public static ChatResultMessage success(String requestId, Long memoryId, 
                                            String result, long processingTimeMs) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .result(result)
                .status(ResultStatus.SUCCESS)
                .processedAt(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .build();
    }
    
    public static ChatResultMessage processing(String requestId, Long memoryId) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ResultStatus.PROCESSING)
                .processedAt(LocalDateTime.now())
                .build();
    }
    
    public static ChatResultMessage failed(String requestId, Long memoryId, String errorMessage) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ResultStatus.FAILED)
                .errorMessage(errorMessage)
                .processedAt(LocalDateTime.now())
                .build();
    }
}
