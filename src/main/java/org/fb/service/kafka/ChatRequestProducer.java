package org.fb.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRequestProducer {
    
    private final KafkaTemplate<String, ChatRequestMessage> requestKafkaTemplate;
    private final KafkaTemplate<String, ChatResultMessage> resultKafkaTemplate;
    
    private static final String AI_REQUEST_TOPIC = "ai-chat-request";
    private static final String AI_RESULT_TOPIC = "ai-chat-result";
    
    public CompletableFuture<SendResult<String, ChatRequestMessage>> sendRequest(ChatRequestMessage request) {
        log.info("发送AI请求到Kafka, requestId: {}, memoryId: {}", 
                request.getRequestId(), request.getMemoryId());
        
        return requestKafkaTemplate.send(AI_REQUEST_TOPIC, request.getRequestId(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("请求发送成功, requestId: {}, partition: {}", 
                                request.getRequestId(), result.getRecordMetadata().partition());
                    } else {
                        log.error("请求发送失败, requestId: {}", request.getRequestId(), ex);
                    }
                });
    }
    
    public CompletableFuture<SendResult<String, ChatResultMessage>> sendResult(ChatResultMessage result) {
        log.info("发送处理结果到Kafka, requestId: {}, status: {}", 
                result.getRequestId(), result.getStatus());
        
        return resultKafkaTemplate.send(AI_RESULT_TOPIC, result.getRequestId(), result)
                .whenComplete((sendResult, ex) -> {
                    if (ex == null) {
                        log.info("结果发送成功, requestId: {}", result.getRequestId());
                    } else {
                        log.error("结果发送失败, requestId: {}", result.getRequestId(), ex);
                    }
                });
    }
    
    public void sendProcessingStatus(String requestId, Long memoryId) {
        ChatResultMessage processingResult = ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ChatResultMessage.ResultStatus.PROCESSING)
                .processedAt(java.time.LocalDateTime.now())
                .build();
        sendResult(processingResult);
    }
    
    public void sendFailedStatus(String requestId, Long memoryId, String errorMessage) {
        ChatResultMessage failedResult = ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ChatResultMessage.ResultStatus.FAILED)
                .errorMessage(errorMessage)
                .processedAt(java.time.LocalDateTime.now())
                .build();
        sendResult(failedResult);
    }
}
