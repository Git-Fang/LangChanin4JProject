package org.fb.service.kafka;

import lombok.extern.slf4j.Slf4j;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class StandaloneChatRequestProducer {

    private static final String AI_REQUEST_TOPIC = "ai-chat-request";
    private static final String AI_RESULT_TOPIC = "ai-chat-result";

    public CompletableFuture<SendResult<String, ChatRequestMessage>> sendRequest(ChatRequestMessage request) {
        log.info("[Standalone模式] 跳过Kafka请求, requestId: {}, memoryId: {}", 
                request.getRequestId(), request.getMemoryId());
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<SendResult<String, ChatResultMessage>> sendResult(ChatResultMessage result) {
        log.info("[Standalone模式] 跳过Kafka结果, requestId: {}, status: {}", 
                result.getRequestId(), result.getStatus());
        return CompletableFuture.completedFuture(null);
    }
    
    public void sendProcessingStatus(String requestId, Long memoryId) {
        log.info("[Standalone模式] 跳过发送处理状态, requestId: {}", requestId);
    }
    
    public void sendFailedStatus(String requestId, Long memoryId, String errorMessage) {
        log.info("[Standalone模式] 跳过发送失败状态, requestId: {}, error: {}", requestId, errorMessage);
    }
}
