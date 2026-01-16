package org.fb.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.constant.BusinessConstant;
import org.fb.service.assistant.*;
import org.fb.service.impl.NL2SQLService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@Profile("!standalone")
@RequiredArgsConstructor
public class ChatRequestConsumer {
    
    private final ChatTypeAssistant chatTypeAssistant;
    private final DoctorAgent doctorAgent;
    private final TranslaterService translaterService;
    private final TermExtractionAgent termExtractionAgent;
    private final ChatAssistant chatAssistant;
    private final NL2SQLService nl2SQLService;
    private final ChatRequestProducer requestProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService mdcExecutorService;
    
    private static final String RESULT_CACHE_PREFIX = "chat:result:";
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    
    @KafkaListener(
            topics = "ai-chat-request",
            groupId = "ai-request-consumer",
            containerFactory = "chatRequestListenerContainerFactory",
            autoStartup = "false"
    )
    public void consumeChatRequest(
            ConsumerRecord<String, ChatRequestMessage> record,
            Acknowledgment ack) {
        
        ChatRequestMessage request = record.value();
        log.info("收到AI请求, requestId: {}, partition: {}, offset: {}", 
                request.getRequestId(), record.partition(), record.offset());
        
        long startTime = System.currentTimeMillis();
        
        try {
            CompletableFuture.supplyAsync(() -> processRequest(request), mdcExecutorService)
                    .thenAccept(result -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        result.setProcessingTimeMs(processingTime);
                        cacheResult(request.getRequestId(), result);
                        requestProducer.sendResult(result);
                        log.info("请求处理完成, requestId: {}, 处理时间: {}ms", 
                                request.getRequestId(), processingTime);
                        ack.acknowledge();
                    })
                    .exceptionally(ex -> {
                        log.error("请求处理异常, requestId: {}", request.getRequestId(), ex);
                        requestProducer.sendFailedStatus(
                                request.getRequestId(), 
                                request.getMemoryId(), 
                                ex.getMessage()
                        );
                        ack.acknowledge();
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("消费消息异常, requestId: {}", request.getRequestId(), e);
            requestProducer.sendFailedStatus(request.getRequestId(), request.getMemoryId(), e.getMessage());
            ack.acknowledge();
        }
    }
    
    private ChatResultMessage processRequest(ChatRequestMessage request) {
        String result;
        try {
            String intent = recognizeIntent(request.getMemoryId(), request.getMessage());
            log.info("意图识别结果: {}, requestId: {}", intent, request.getRequestId());
            
            switch (intent.toLowerCase()) {
                case BusinessConstant.MEDICAL_TYPE:
                    result = doctorAgent.chat(request.getMemoryId(), request.getMessage());
                    break;
                case BusinessConstant.TRANSLATION_TYPE:
                    result = translaterService.translate(request.getMemoryId(), request.getMessage());
                    break;
                case BusinessConstant.TERM_EXTRACTION_TYPE:
                    result = termExtractionAgent.chat(request.getMessage());
                    break;
                case BusinessConstant.SQL_OPERATION_TYPE:
                    result = nl2SQLService.executeNaturalLanguageQuery(request.getMessage()).toString();
                    break;
                default:
                    result = chatAssistant.chat(request.getMemoryId(), request.getMessage());
            }
            
            return ChatResultMessage.builder()
                    .requestId(request.getRequestId())
                    .memoryId(request.getMemoryId())
                    .result(result)
                    .status(ChatResultMessage.ResultStatus.SUCCESS)
                    .processedAt(LocalDateTime.now())
                    .intent(intent)
                    .build();
                    
        } catch (Exception e) {
            log.error("业务处理异常, requestId: {}", request.getRequestId(), e);
            throw new RuntimeException("AI请求处理失败: " + e.getMessage(), e);
        }
    }
    
    private String recognizeIntent(Long memoryId, String message) {
        Long tempMemoryId = System.currentTimeMillis();
        String aiResponse = chatTypeAssistant.chat(tempMemoryId, message);
        return extractIntent(aiResponse);
    }
    
    private String extractIntent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return BusinessConstant.DEFAULT_TYPE;
        }
        
        try {
            Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                return matcher.group(1).trim().toLowerCase();
            }
        } catch (Exception e) {
            log.warn("解析JSON intent失败", e);
        }
        
        String lowerResponse = aiResponse.toLowerCase();
        if (lowerResponse.contains(BusinessConstant.MEDICAL_TYPE)) {
            return BusinessConstant.MEDICAL_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TRANSLATION_TYPE)) {
            return BusinessConstant.TRANSLATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.TERM_EXTRACTION_TYPE)) {
            return BusinessConstant.TERM_EXTRACTION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.SQL_OPERATION_TYPE)) {
            return BusinessConstant.SQL_OPERATION_TYPE;
        } else if (lowerResponse.contains(BusinessConstant.DEFAULT_TYPE)) {
            return BusinessConstant.DEFAULT_TYPE;
        }
        
        return BusinessConstant.DEFAULT_TYPE;
    }
    
    private void cacheResult(String requestId, ChatResultMessage result) {
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, RESULT_TTL);
            log.info("结果已缓存, requestId: {}", requestId);
        } catch (Exception e) {
            log.error("缓存结果失败, requestId: {}", requestId, e);
        }
    }
}
