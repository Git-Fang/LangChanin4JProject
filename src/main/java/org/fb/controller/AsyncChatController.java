package org.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatForm;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.service.ChatService;
import org.fb.service.StreamingChatService;
import org.fb.service.kafka.ChatRequestProducer;
import org.fb.service.kafka.StandaloneChatRequestProducer;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/xiaozhi")
public class AsyncChatController implements EnvironmentAware {
    
    private final ChatRequestProducer requestProducer;
    private final StandaloneChatRequestProducer standaloneRequestProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final StreamingChatService streamingChatService;
    
    private Environment environment;

    @Autowired
    public AsyncChatController(
            @Lazy ChatRequestProducer requestProducer,
            @Autowired(required = false) StandaloneChatRequestProducer standaloneRequestProducer,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatService chatService,
            @Autowired(required = false) StreamingChatService streamingChatService) {
        this.requestProducer = requestProducer;
        this.standaloneRequestProducer = standaloneRequestProducer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.streamingChatService = streamingChatService;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    
    private boolean isStandalone() {
        String[] profiles = environment.getActiveProfiles();
        for (String profile : profiles) {
            if ("standalone".equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private ChatRequestProducer getProducer() {
        return isStandalone() ? null : requestProducer;
    }
    
    private static final String RESULT_CACHE_PREFIX = "chat:result:";
    private static final String STREAM_CACHE_PREFIX = "chat:stream:";
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final long SSE_TIMEOUT = 300000L; // 5分钟
    
    private static final ConcurrentHashMap<String, SseEmitter> sseConnections = new ConcurrentHashMap<>();
    
    @PostMapping("/chat/async")
    public Map<String, Object> asyncChat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        
        log.info("收到异步聊天请求, memoryId: {}, message: {}", memoryId, userMessage);
        
        ChatRequestMessage request = ChatRequestMessage.create(memoryId, userMessage);
        
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            redisTemplate.opsForValue().set("chat:request:" + request.getRequestId(), requestJson, RESULT_TTL);
        } catch (Exception e) {
            log.error("保存请求数据失败, requestId: {}", request.getRequestId(), e);
        }
        
        if (isStandalone()) {
            log.info("[Standalone模式] 同步处理请求");
            processSynchronously(request);
        } else {
            ((ChatRequestProducer) getProducer()).sendRequest(request);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", request.getRequestId());
        response.put("status", "PROCESSING");
        response.put("message", "请求已提交");
        response.put("resultUrl", "/xiaozhi/result/" + request.getRequestId());
        response.put("streamUrl", "/xiaozhi/chat/stream/" + request.getRequestId());
        
        return response;
    }
    
    private void processSynchronously(ChatRequestMessage request) {
        if (streamingChatService != null) {
            log.info("[Standalone模式] 使用流式处理, requestId: {}", request.getRequestId());
            
            try {
                redisTemplate.opsForValue().set("chat:request:" + request.getRequestId(), 
                    objectMapper.writeValueAsString(request), RESULT_TTL);
                
                ChatResultMessage processingResult = ChatResultMessage.builder()
                        .requestId(request.getRequestId())
                        .memoryId(request.getMemoryId())
                        .status(ChatResultMessage.ResultStatus.PROCESSING)
                        .build();
                cacheResult(request.getRequestId(), processingResult);
                
                Flux<String> flux = streamingChatService.chat(request.getMemoryId(), request.getMessage());
                
                StringBuilder accumulated = new StringBuilder();
                long startTime = System.currentTimeMillis();
                
                flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        accumulated.append(chunk);
                        updateStreamContent(request.getRequestId(), chunk);
                        log.debug("流式内容增量, requestId: {}, 累计长度: {}", 
                            request.getRequestId(), accumulated.length());
                    })
                    .doOnComplete(() -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        String finalResult = accumulated.toString();
                        
                        ChatResultMessage resultMessage = ChatResultMessage.builder()
                                .requestId(request.getRequestId())
                                .memoryId(request.getMemoryId())
                                .result(finalResult)
                                .status(ChatResultMessage.ResultStatus.SUCCESS)
                                .processingTimeMs(processingTime)
                                .build();
                        
                        cacheResult(request.getRequestId(), resultMessage);
                        log.info("[Standalone模式] 流式处理完成, requestId: {}, 结果长度: {}, 耗时: {}ms", 
                                request.getRequestId(), finalResult.length(), processingTime);
                    })
                    .doOnError(error -> {
                        log.error("[Standalone模式] 流式处理失败, requestId: {}", request.getRequestId(), error);
                        ChatResultMessage failedResult = ChatResultMessage.builder()
                                .requestId(request.getRequestId())
                                .memoryId(request.getMemoryId())
                                .status(ChatResultMessage.ResultStatus.FAILED)
                                .errorMessage(error.getMessage())
                                .build();
                        cacheResult(request.getRequestId(), failedResult);
                    })
                    .subscribe();
                
            } catch (Exception e) {
                log.error("[Standalone模式] 流式处理异常, requestId: {}", request.getRequestId(), e);
                ChatResultMessage failedResult = ChatResultMessage.builder()
                        .requestId(request.getRequestId())
                        .memoryId(request.getMemoryId())
                        .status(ChatResultMessage.ResultStatus.FAILED)
                        .errorMessage(e.getMessage())
                        .build();
                cacheResult(request.getRequestId(), failedResult);
            }
        } else {
            log.info("[Standalone模式] 使用普通同步处理, requestId: {}", request.getRequestId());
            
            CompletableFuture.runAsync(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    String result = chatService.chat(request.getMemoryId(), request.getMessage());
                    long processingTime = System.currentTimeMillis() - startTime;
                    
                    ChatResultMessage resultMessage = ChatResultMessage.builder()
                            .requestId(request.getRequestId())
                            .memoryId(request.getMemoryId())
                            .result(result)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();
                    
                    cacheResult(request.getRequestId(), resultMessage);
                    log.info("[Standalone模式] 同步处理完成, requestId: {}, 处理时间: {}ms", 
                            request.getRequestId(), processingTime);
                } catch (Exception e) {
                    log.error("[Standalone模式] 处理失败, requestId: {}", request.getRequestId(), e);
                    ChatResultMessage failedResult = ChatResultMessage.builder()
                            .requestId(request.getRequestId())
                            .memoryId(request.getMemoryId())
                            .status(ChatResultMessage.ResultStatus.FAILED)
                            .errorMessage(e.getMessage())
                            .build();
                    cacheResult(request.getRequestId(), failedResult);
                }
            });
        }
    }
    
    private void cacheResult(String requestId, ChatResultMessage result) {
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(RESULT_CACHE_PREFIX + requestId, jsonResult, RESULT_TTL);
        } catch (Exception e) {
            log.error("缓存结果失败, requestId: {}", requestId, e);
        }
    }
    
    private void updateStreamContent(String requestId, String content) {
        try {
            String cacheKey = STREAM_CACHE_PREFIX + requestId;
            String existingContent = redisTemplate.opsForValue().get(cacheKey);
            String newContent = (existingContent != null ? existingContent : "") + content;
            redisTemplate.opsForValue().set(cacheKey, newContent, RESULT_TTL);
        } catch (Exception e) {
            log.error("更新流式内容失败, requestId: {}", requestId, e);
        }
    }
    
    private String getStreamContent(String requestId) {
        try {
            String cacheKey = STREAM_CACHE_PREFIX + requestId;
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.error("获取流式内容失败, requestId: {}", requestId, e);
            return "";
        }
    }
    
    @GetMapping(value = "/chat/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResult(@PathVariable String requestId) {
        log.info("建立SSE连接, requestId: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        sseConnections.put(requestId, emitter);
        
        try {
            sendSseEvent(emitter, "connected", Map.of(
                "status", "connected",
                "requestId", requestId,
                "timestamp", System.currentTimeMillis()
            ));
            
            log.info("SSE连接已建立, requestId: {}", requestId);
            
            String resultJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (resultJson != null) {
                ChatResultMessage result = objectMapper.readValue(resultJson, ChatResultMessage.class);
                if (result.getStatus() == ChatResultMessage.ResultStatus.SUCCESS ||
                    result.getStatus() == ChatResultMessage.ResultStatus.FAILED) {
                    processResultEvent(emitter, requestId, result);
                    emitter.complete();
                    sseConnections.remove(requestId);
                    return emitter;
                }
            }
            
            long startTime = System.currentTimeMillis();
            long timeout = SSE_TIMEOUT - 5000;
            long checkInterval = 100;
            int lastContentLength = 0;
            
            while (System.currentTimeMillis() - startTime < timeout) {
                String currentResultJson = redisTemplate.opsForValue().get(cacheKey);
                
                if (currentResultJson != null) {
                    ChatResultMessage result = objectMapper.readValue(currentResultJson, ChatResultMessage.class);
                    
                    if (result.getStatus() == ChatResultMessage.ResultStatus.PROCESSING) {
                        sendSseEvent(emitter, "processing", Map.of(
                            "status", "processing",
                            "message", "AI正在处理您的请求..."
                        ));
                    } else {
                        if (result.getIntent() != null) {
                            sendSseEvent(emitter, "progress", Map.of(
                                "status", "progress",
                                "intent", result.getIntent()
                            ));
                        }
                        
                        if (result.getResult() != null && !result.getResult().isEmpty()) {
                            String streamContent = getStreamContent(requestId);
                            
                            if (streamContent != null && streamContent.length() > lastContentLength) {
                                String newContent = streamContent.substring(lastContentLength);
                                if (!newContent.isEmpty()) {
                                    sendSseEvent(emitter, "chunk", Map.of(
                                        "content", newContent,
                                        "status", "streaming",
                                        "fullContent", streamContent,
                                        "progress", Math.min(streamContent.length(), 100)
                                    ));
                                    lastContentLength = streamContent.length();
                                }
                            }
                        }
                        
                        if (result.getStatus() == ChatResultMessage.ResultStatus.SUCCESS ||
                            result.getStatus() == ChatResultMessage.ResultStatus.FAILED) {
                            Map<String, Object> resultData = new HashMap<>();
                            resultData.put("requestId", result.getRequestId());
                            resultData.put("memoryId", result.getMemoryId());
                            resultData.put("status", result.getStatus() != null ? result.getStatus().name() : null);
                            resultData.put("result", result.getResult());
                            resultData.put("processingTimeMs", result.getProcessingTimeMs());
                            resultData.put("intent", result.getIntent());
                            if (result.getErrorMessage() != null) {
                                resultData.put("error", result.getErrorMessage());
                            }
                            
                            sendSseEvent(emitter, "result", resultData);
                            sendSseEvent(emitter, "complete", Map.of("event", "complete"));
                            
                            emitter.complete();
                            sseConnections.remove(requestId);
                            log.info("SSE推送完成, requestId: {}, 处理时间: {}ms", 
                                    requestId, result.getProcessingTimeMs());
                            return emitter;
                        }
                    }
                }
                
                Thread.sleep(checkInterval);
            }
            
            log.warn("SSE超时, requestId: {}", requestId);
            sendSseEvent(emitter, "timeout", Map.of(
                "status", "timeout",
                "message", "处理超时"
            ));
            sendSseEvent(emitter, "complete", Map.of("event", "complete", "reason", "timeout"));
            emitter.complete();
            sseConnections.remove(requestId);
            
        } catch (Exception e) {
            log.error("SSE流异常, requestId: {}", requestId, e);
            try {
                sendSseEvent(emitter, "error", Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
            } catch (IOException ioException) {
                log.error("发送错误事件失败", ioException);
            }
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("SSE超时回调, requestId: {}", requestId);
            try {
                sendSseEvent(emitter, "timeout", Map.of("status", "timeout"));
            } catch (Exception e) {
                log.error("发送超时事件失败", e);
            }
            emitter.complete();
            sseConnections.remove(requestId);
        });
        
        emitter.onCompletion(() -> {
            log.info("SSE连接完成, requestId: {}", requestId);
            sseConnections.remove(requestId);
        });
        
        emitter.onError(e -> {
            log.error("SSE连接错误, requestId: {}", requestId, e);
            sseConnections.remove(requestId);
        });
        
        return emitter;
    }
    
    @GetMapping(value = "/chat/streaming/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamingChat(@PathVariable String requestId) {
        log.info("建立真·SSE流连接, requestId: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT * 2);
        sseConnections.put(requestId, emitter);
        
        try {
            sendSseEvent(emitter, "connected", Map.of(
                "status", "connected",
                "requestId", requestId,
                "timestamp", System.currentTimeMillis(),
                "mode", "streaming"
            ));
            
            String cacheKey = RESULT_CACHE_PREFIX + requestId;
            String resultJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (resultJson != null) {
                ChatResultMessage cachedResult = objectMapper.readValue(resultJson, ChatResultMessage.class);
                if (cachedResult.getStatus() == ChatResultMessage.ResultStatus.SUCCESS) {
                    String content = cachedResult.getResult();
                    if (content != null && !content.isEmpty()) {
                        sendSseEvent(emitter, "chunk", Map.of(
                            "content", content,
                            "status", "streaming",
                            "fullContent", content,
                            "progress", 100
                        ));
                    }
                    processResultEvent(emitter, requestId, cachedResult);
                    emitter.complete();
                    sseConnections.remove(requestId);
                    return emitter;
                }
            }
            
            log.info("开始流式处理, requestId: {}", requestId);
            
            String requestJson = redisTemplate.opsForValue().get("chat:request:" + requestId);
            
            if (requestJson == null) {
                log.warn("请求数据不存在, requestId: {}", requestId);
                sendSseEvent(emitter, "error", Map.of(
                    "status", "error",
                    "message", "请求数据不存在，请重新发送消息"
                ));
                emitter.complete();
                sseConnections.remove(requestId);
                return emitter;
            }
            
            ChatRequestMessage request = objectMapper.readValue(requestJson, ChatRequestMessage.class);
            
            if (request != null && streamingChatService != null) {
                log.info("使用流式处理模式, requestId: {}", requestId);
                redisTemplate.opsForValue().set(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);
                
                Flux<String> flux = streamingChatService.chat(request.getMemoryId(), request.getMessage());
                
                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
                
                flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);
                            
                            redisTemplate.opsForValue().set(
                                STREAM_CACHE_PREFIX + requestId, 
                                updated, 
                                RESULT_TTL
                            );
                            
                            sendSseEvent(emitter, "chunk", Map.of(
                                "content", chunk,
                                "status", "streaming",
                                "fullContent", updated,
                                "progress", 50
                            ));
                            
                            log.debug("流式内容块, requestId: {}, chunk长度: {}, 累计长度: {}", 
                                requestId, chunk.length(), updated.length());
                        } catch (Exception ex) {
                            log.error("发送流式内容块失败, requestId: {}", requestId, ex);
                        }
                    })
                    .doOnComplete(() -> {
                        long processingTime = System.currentTimeMillis() - startTime.get();
                        log.info("流式处理完成, requestId: {}, 总长度: {}, 耗时: {}ms", 
                            requestId, accumulated.get().length(), processingTime);
                        
                        String finalContent = accumulated.get();
                        
                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(requestId)
                            .memoryId(request.getMemoryId())
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();
                        
                        try {
                            String jsonResult = objectMapper.writeValueAsString(finalResult);
                            redisTemplate.opsForValue().set(cacheKey, jsonResult, RESULT_TTL);
                            
                            processResultEvent(emitter, requestId, finalResult);
                            emitter.complete();
                        } catch (Exception ex) {
                            log.error("完成流式处理失败, requestId: {}", requestId, ex);
                            emitter.completeWithError(ex);
                        }
                        sseConnections.remove(requestId);
                    })
                    .doOnError(error -> {
                        log.error("流式处理错误, requestId: {}", requestId, error);
                        try {
                            sendSseEvent(emitter, "error", Map.of(
                                "status", "error",
                                "message", error.getMessage()
                            ));
                        } catch (IOException ex) {
                            log.error("发送错误事件失败", ex);
                        }
                        emitter.completeWithError(error);
                        sseConnections.remove(requestId);
                    })
                    .subscribe();
            } else if (request != null) {
                log.info("流式服务不可用，使用普通模式, requestId: {}", requestId);
                
                sendSseEvent(emitter, "warning", Map.of(
                    "status", "warning",
                    "message", "流式服务不可用，将使用普通模式处理"
                ));
                
                ChatResultMessage processingResult = ChatResultMessage.builder()
                        .requestId(requestId)
                        .memoryId(request.getMemoryId())
                        .status(ChatResultMessage.ResultStatus.PROCESSING)
                        .build();
                cacheResult(requestId, processingResult);
                
                CompletableFuture.runAsync(() -> {
                    try {
                        long startTimeMs = System.currentTimeMillis();
                        String result = chatService.chat(request.getMemoryId(), request.getMessage());
                        long processingTime = System.currentTimeMillis() - startTimeMs;
                        
                        updateStreamContent(requestId, result);
                        
                        ChatResultMessage finalResult = ChatResultMessage.builder()
                                .requestId(requestId)
                                .memoryId(request.getMemoryId())
                                .result(result)
                                .status(ChatResultMessage.ResultStatus.SUCCESS)
                                .processingTimeMs(processingTime)
                                .build();
                        
                        cacheResult(requestId, finalResult);
                        
                        sendSseEvent(emitter, "chunk", Map.of(
                            "content", result,
                            "status", "streaming",
                            "fullContent", result,
                            "progress", 100
                        ));
                        
                        processResultEvent(emitter, requestId, finalResult);
                        emitter.complete();
                        sseConnections.remove(requestId);
                        
                        log.info("普通模式处理完成, requestId: {}, 耗时: {}ms", requestId, processingTime);
                    } catch (Exception ex) {
                        log.error("普通模式处理失败, requestId: {}", requestId, ex);
                        try {
                            ChatResultMessage failedResult = ChatResultMessage.builder()
                                    .requestId(requestId)
                                    .memoryId(request.getMemoryId())
                                    .status(ChatResultMessage.ResultStatus.FAILED)
                                    .errorMessage(ex.getMessage())
                                    .build();
                            cacheResult(requestId, failedResult);
                            
                            sendSseEvent(emitter, "error", Map.of(
                                "status", "error",
                                "message", ex.getMessage()
                            ));
                        } catch (IOException ioException) {
                            log.error("发送错误事件失败", ioException);
                        }
                        emitter.completeWithError(ex);
                        sseConnections.remove(requestId);
                    }
                });
            } else {
                log.warn("无法获取请求数据, requestId: {}", requestId);
                sendSseEvent(emitter, "error", Map.of(
                    "status", "error",
                    "message", "请求数据不存在"
                ));
                emitter.complete();
                sseConnections.remove(requestId);
            }
            
        } catch (Exception e) {
            log.error("SSE流初始化异常, requestId: {}", requestId, e);
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("SSE流超时, requestId: {}", requestId);
            try {
                sendSseEvent(emitter, "timeout", Map.of("status", "timeout", "message", "处理超时"));
            } catch (Exception ex) {
                log.error("发送超时事件失败", ex);
            }
            emitter.complete();
            sseConnections.remove(requestId);
        });
        
        emitter.onCompletion(() -> sseConnections.remove(requestId));
        emitter.onError(e -> {
            log.error("SSE流错误, requestId: {}", requestId, e);
            sseConnections.remove(requestId);
        });
        
        return emitter;
    }
    
    @GetMapping(value = "/chat/stream-direct/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResultDirect(@PathVariable String requestId) {
        log.info("建立直接SSE流连接, requestId: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseConnections.put(requestId, emitter);
        
        try {
            sendSseEvent(emitter, "connected", Map.of(
                "status", "connected",
                "requestId", requestId,
                "timestamp", System.currentTimeMillis()
            ));
            
            String cacheKey = RESULT_CACHE_PREFIX + requestId;
            String resultJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (resultJson != null) {
                ChatResultMessage result = objectMapper.readValue(resultJson, ChatResultMessage.class);
                processResultEvent(emitter, requestId, result);
                emitter.complete();
                sseConnections.remove(requestId);
                return emitter;
            }
            
            redisTemplate.opsForValue().set(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);
            
            CompletableFuture.runAsync(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    long timeout = SSE_TIMEOUT - 5000;
                    long checkInterval = 100;
                    AtomicReference<String> accumulatedResult = new AtomicReference<>("");
                    
                    while (System.currentTimeMillis() - startTime < timeout) {
                        String currentResultJson = redisTemplate.opsForValue().get(cacheKey);
                        
                        if (currentResultJson != null) {
                            ChatResultMessage currentResult = objectMapper.readValue(currentResultJson, ChatResultMessage.class);
                            
                            if (currentResult.getStatus() == ChatResultMessage.ResultStatus.PROCESSING) {
                                sendSseEvent(emitter, "processing", Map.of(
                                    "status", "processing",
                                    "message", "AI正在处理您的请求..."
                                ));
                            } else {
                                String newResult = currentResult.getResult();
                                String oldResult = accumulatedResult.get();
                                
                                if (newResult != null && !newResult.equals(oldResult)) {
                                    String diff = newResult.substring(oldResult.length());
                                    if (!diff.isEmpty()) {
                                        sendSseEvent(emitter, "chunk", Map.of(
                                            "content", diff,
                                            "status", "streaming",
                                            "fullContent", newResult,
                                            "progress", newResult.length()
                                        ));
                                    }
                                    accumulatedResult.set(newResult);
                                }
                                
                                if (currentResult.getStatus() == ChatResultMessage.ResultStatus.SUCCESS ||
                                    currentResult.getStatus() == ChatResultMessage.ResultStatus.FAILED) {
                                    processResultEvent(emitter, requestId, currentResult);
                                    emitter.complete();
                                    sseConnections.remove(requestId);
                                    return;
                                }
                            }
                        }
                        
                        Thread.sleep(checkInterval);
                    }
                    
                    sendSseEvent(emitter, "timeout", Map.of("status", "timeout", "message", "处理超时"));
                    emitter.complete();
                    sseConnections.remove(requestId);
                    
                } catch (Exception e) {
                    log.error("直接SSE流异常, requestId: {}", requestId, e);
                    try {
                        sendSseEvent(emitter, "error", Map.of("status", "error", "message", e.getMessage()));
                    } catch (IOException ioException) {
                        log.error("发送错误事件失败", ioException);
                    }
                    emitter.completeWithError(e);
                    sseConnections.remove(requestId);
                }
            });
            
        } catch (Exception e) {
            log.error("直接SSE初始化异常, requestId: {}", requestId, e);
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("直接SSE超时, requestId: {}", requestId);
            try {
                sendSseEvent(emitter, "timeout", Map.of("status", "timeout"));
            } catch (Exception ex) {
                log.error("发送超时事件失败", ex);
            }
            emitter.complete();
            sseConnections.remove(requestId);
        });
        
        emitter.onCompletion(() -> sseConnections.remove(requestId));
        emitter.onError(e -> {
            log.error("直接SSE错误, requestId: {}", requestId, e);
            sseConnections.remove(requestId);
        });
        
        return emitter;
    }
    
    private void processResultEvent(SseEmitter emitter, String requestId, ChatResultMessage result) throws IOException {
        if (result.getIntent() != null) {
            sendSseEvent(emitter, "progress", Map.of(
                "status", "progress",
                "intent", result.getIntent()
            ));
        }
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("requestId", result.getRequestId());
        resultData.put("memoryId", result.getMemoryId());
        resultData.put("status", result.getStatus() != null ? result.getStatus().name() : null);
        resultData.put("result", result.getResult());
        resultData.put("processingTimeMs", result.getProcessingTimeMs());
        resultData.put("intent", result.getIntent());
        if (result.getErrorMessage() != null) {
            resultData.put("error", result.getErrorMessage());
        }
        
        sendSseEvent(emitter, "result", resultData);
        sendSseEvent(emitter, "complete", Map.of("event", "complete"));
        log.info("SSE结果推送完成, requestId: {}, 处理时间: {}ms", 
                requestId, result.getProcessingTimeMs());
    }
    
    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        String jsonData = objectMapper.writeValueAsString(data);
        emitter.send(SseEmitter.event().name(eventName).data(jsonData));
    }
    
    @GetMapping("/result/{requestId}")
    public Map<String, Object> getResult(@PathVariable String requestId) {
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        String resultJson = redisTemplate.opsForValue().get(cacheKey);
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        
        if (resultJson != null) {
            try {
                ChatResultMessage result = objectMapper.readValue(resultJson, ChatResultMessage.class);
                
                response.put("status", result.getStatus());
                response.put("result", result.getResult());
                response.put("processingTimeMs", result.getProcessingTimeMs());
                response.put("intent", result.getIntent());
                
                if (result.getErrorMessage() != null) {
                    response.put("error", result.getErrorMessage());
            }

        } catch (Exception e) {
                log.error("解析结果失败, requestId: {}", requestId, e);
                response.put("status", "ERROR");
                response.put("error", "解析结果失败");
            }
        } else {
            response.put("status", "PROCESSING");
            response.put("message", "请求正在处理中，请稍后重试");
            response.put("retryAfter", 2000);
        }
        
        return response;
    }
    
    @GetMapping("/sse/count")
    public Map<String, Object> getSseCount() {
        Map<String, Object> response = new HashMap<>();
        response.put("activeConnections", sseConnections.size());
        return response;
    }
    
    @PostMapping("/chat/http-stream")
    @Operation(summary = "HTTP流式聊天", description = "通过POST发送消息，SSE流式返回结果")
    public Map<String, Object> startHttpStreamChat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        
        log.info("收到HTTP流式聊天请求, memoryId: {}, message: {}", memoryId, userMessage);
        
        String requestId = "http_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
        
        try {
            String requestJson = objectMapper.writeValueAsString(
                ChatRequestMessage.create(memoryId, userMessage)
            );
            redisTemplate.opsForValue().set("chat:request:" + requestId, requestJson, RESULT_TTL);
            redisTemplate.opsForValue().set(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);
        } catch (Exception e) {
            log.error("保存请求数据失败, requestId: {}", requestId, e);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        response.put("streamUrl", "/xiaozhi/chat/http-stream/" + requestId);
        
        return response;
    }
    
    @GetMapping(value = "/chat/http-stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamHttpChat(@PathVariable String requestId) {
        log.info("HTTP流式聊天SSE连接, requestId: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseConnections.put(requestId, emitter);
        
        try {
            sendSseEvent(emitter, "connected", Map.of(
                "status", "connected",
                "requestId", requestId,
                "timestamp", System.currentTimeMillis(),
                "mode", "http-stream"
            ));
            
            log.info("HTTP流式连接已建立, requestId: {}", requestId);
            
            String requestJson = redisTemplate.opsForValue().get("chat:request:" + requestId);
            if (requestJson == null) {
                log.warn("请求数据不存在, requestId: {}", requestId);
                sendSseEvent(emitter, "error", Map.of(
                    "status", "error",
                    "message", "请求数据不存在"
                ));
                emitter.complete();
                sseConnections.remove(requestId);
                return emitter;
            }
            
            ChatRequestMessage request = objectMapper.readValue(requestJson, ChatRequestMessage.class);
            Long memoryId = request.getMemoryId();
            String message = request.getMessage();
            
            if (streamingChatService != null) {
                log.info("使用流式服务处理HTTP请求, requestId: {}", requestId);
                
                redisTemplate.opsForValue().set(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);
                
                Flux<String> flux = streamingChatService.chat(memoryId, message);
                
                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
                
                flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);
                            
                            redisTemplate.opsForValue().set(
                                STREAM_CACHE_PREFIX + requestId, 
                                updated, 
                                RESULT_TTL
                            );
                            
                            sendSseEvent(emitter, "chunk", Map.of(
                                "content", chunk,
                                "status", "streaming",
                                "fullContent", updated,
                                "progress", 50
                            ));
                            
                            log.debug("HTTP流式内容块, requestId: {}, chunk长度: {}, 累计长度: {}", 
                                requestId, chunk.length(), updated.length());
                        } catch (Exception ex) {
                            log.error("发送HTTP流式内容块失败, requestId: {}", requestId, ex);
                        }
                    })
                    .doOnComplete(() -> {
                        long processingTime = System.currentTimeMillis() - startTime.get();
                        log.info("HTTP流式处理完成, requestId: {}, 总长度: {}, 耗时: {}ms", 
                            requestId, accumulated.get().length(), processingTime);
                        
                        String finalContent = accumulated.get();
                        
                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(requestId)
                            .memoryId(memoryId)
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();
                        
                        try {
                            String jsonResult = objectMapper.writeValueAsString(finalResult);
                            redisTemplate.opsForValue().set(RESULT_CACHE_PREFIX + requestId, jsonResult, RESULT_TTL);
                            
                            sendSseEvent(emitter, "result", Map.of(
                                "requestId", requestId,
                                "memoryId", memoryId,
                                "status", "SUCCESS",
                                "result", finalContent,
                                "processingTimeMs", processingTime
                            ));
                            sendSseEvent(emitter, "complete", Map.of("event", "complete"));
                            emitter.complete();
                        } catch (Exception ex) {
                            log.error("完成HTTP流式处理失败, requestId: {}", requestId, ex);
                            emitter.completeWithError(ex);
                        }
                        sseConnections.remove(requestId);
                    })
                    .doOnError(error -> {
                        log.error("HTTP流式处理错误, requestId: {}", requestId, error);
                        try {
                            sendSseEvent(emitter, "error", Map.of(
                                "status", "error",
                                "message", error.getMessage()
                            ));
                        } catch (IOException ex) {
                            log.error("发送错误事件失败", ex);
                        }
                        emitter.completeWithError(error);
                        sseConnections.remove(requestId);
                    })
                    .subscribe();
            } else {
                log.warn("流式服务不可用，requestId: {}", requestId);
                
                ChatResultMessage processingResult = ChatResultMessage.builder()
                        .requestId(requestId)
                        .memoryId(memoryId)
                        .status(ChatResultMessage.ResultStatus.PROCESSING)
                        .build();
                cacheResult(requestId, processingResult);
                
                CompletableFuture.runAsync(() -> {
                    try {
                        long startTimeMs = System.currentTimeMillis();
                        String result = chatService.chat(memoryId, message);
                        long processingTime = System.currentTimeMillis() - startTimeMs;
                        
                        updateStreamContent(requestId, result);
                        
                        ChatResultMessage finalResult = ChatResultMessage.builder()
                                .requestId(requestId)
                                .memoryId(memoryId)
                                .result(result)
                                .status(ChatResultMessage.ResultStatus.SUCCESS)
                                .processingTimeMs(processingTime)
                                .build();
                        
                        cacheResult(requestId, finalResult);
                        
                        sendSseEvent(emitter, "chunk", Map.of(
                            "content", result,
                            "status", "streaming",
                            "fullContent", result,
                            "progress", 100
                        ));
                        
                        sendSseEvent(emitter, "result", Map.of(
                            "requestId", requestId,
                            "memoryId", memoryId,
                            "status", "SUCCESS",
                            "result", result,
                            "processingTimeMs", processingTime
                        ));
                        sendSseEvent(emitter, "complete", Map.of("event", "complete"));
                        emitter.complete();
                        sseConnections.remove(requestId);
                        
                        log.info("HTTP普通模式处理完成, requestId: {}, 耗时: {}ms", requestId, processingTime);
                    } catch (Exception ex) {
                        log.error("HTTP普通模式处理失败, requestId: {}", requestId, ex);
                        try {
                            ChatResultMessage failedResult = ChatResultMessage.builder()
                                    .requestId(requestId)
                                    .memoryId(memoryId)
                                    .status(ChatResultMessage.ResultStatus.FAILED)
                                    .errorMessage(ex.getMessage())
                                    .build();
                            cacheResult(requestId, failedResult);
                            
                            sendSseEvent(emitter, "error", Map.of(
                                "status", "error",
                                "message", ex.getMessage()
                            ));
                        } catch (IOException ioException) {
                            log.error("发送错误事件失败", ioException);
                        }
                        emitter.completeWithError(ex);
                        sseConnections.remove(requestId);
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("HTTP流式初始化异常, requestId: {}", requestId, e);
            try {
                sendSseEvent(emitter, "error", Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
            } catch (IOException ioException) {
                log.error("发送错误事件失败", ioException);
            }
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("HTTP流式超时, requestId: {}", requestId);
            try {
                sendSseEvent(emitter, "timeout", Map.of("status", "timeout", "message", "处理超时"));
            } catch (Exception ex) {
                log.error("发送超时事件失败", ex);
            }
            emitter.complete();
            sseConnections.remove(requestId);
        });
        
        emitter.onCompletion(() -> {
            log.info("HTTP流式连接完成, requestId: {}", requestId);
            sseConnections.remove(requestId);
        });
        
        emitter.onError(e -> {
            log.error("HTTP流式连接错误, requestId: {}", requestId, e);
            sseConnections.remove(requestId);
        });
        
        return emitter;
    }
}
