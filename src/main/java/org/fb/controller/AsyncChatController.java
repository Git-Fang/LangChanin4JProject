package org.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.fb.bean.ChatForm;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.service.ChatSaveService;
import org.fb.service.ChatService;
import org.fb.service.StreamingChatService;
import org.fb.service.StreamingDispatchService;
import org.fb.service.kafka.ChatRequestProducer;
import org.fb.service.kafka.StandaloneChatRequestProducer;
import org.fb.tools.MongoChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Lazy;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.fb.config.RedisHealthIndicator;

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
    private final StreamingDispatchService streamingDispatchService;
    private final ChatSaveService chatSaveService;
    private final MongoChatMemoryStore mongoChatMemoryStore;
    private final RedisHealthIndicator redisHealthIndicator;

    private Environment environment;

    @Autowired
    public AsyncChatController(
            @Lazy ChatRequestProducer requestProducer,
            @Autowired(required = false) StandaloneChatRequestProducer standaloneRequestProducer,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatService chatService,
            @Autowired(required = false) StreamingChatService streamingChatService,
            @Autowired(required = false) StreamingDispatchService streamingDispatchService,
            @Autowired(required = false) ChatSaveService chatSaveService,
            MongoChatMemoryStore mongoChatMemoryStore,
            RedisHealthIndicator redisHealthIndicator) {
        this.requestProducer = requestProducer;
        this.standaloneRequestProducer = standaloneRequestProducer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.streamingChatService = streamingChatService;
        this.streamingDispatchService = streamingDispatchService;
        this.chatSaveService = chatSaveService;
        this.mongoChatMemoryStore = mongoChatMemoryStore;
        this.redisHealthIndicator = redisHealthIndicator;
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
    private static final long SSE_TIMEOUT = 600000L; // 10分钟
    
    private static final ConcurrentHashMap<String, SseEmitter> sseConnections = new ConcurrentHashMap<>();
    // 内存缓存，用于Redis不可用时的降级方案
    private static final ConcurrentHashMap<String, ChatRequestMessage> memoryCache = new ConcurrentHashMap<>();
    
    @PostMapping("/chat/async")
    public Map<String, Object> asyncChat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        java.util.List<String> extractedTexts = chatForm.getExtractedTexts();

        log.info("收到异步聊天请求, memoryId: {}, message: {}", memoryId, userMessage);
        if (extractedTexts != null && !extractedTexts.isEmpty()) {
            log.info("附带文件提取内容数量: {}", extractedTexts.size());
        }

        String fullMessage = buildFullMessage(userMessage, extractedTexts);
        ChatRequestMessage request = ChatRequestMessage.create(memoryId, fullMessage);

        try {
            String requestJson = objectMapper.writeValueAsString(request);
            boolean savedToRedis = safeSetRedisValue("chat:request:" + request.getRequestId(), requestJson, RESULT_TTL);
            
            // 无论Redis是否可用，都将请求保存到内存缓存中作为降级方案
            memoryCache.put(request.getRequestId(), request);
            log.info("请求数据已保存, requestId: {}, Redis保存: {}", request.getRequestId(), savedToRedis);
        } catch (Exception e) {
            log.error("保存请求数据失败, requestId: {}", request.getRequestId(), e);
            // 即使发生异常，也要将请求保存到内存缓存中
            memoryCache.put(request.getRequestId(), request);
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
    
    private void processSynchronously(ChatRequestMessage request) {
        if (streamingDispatchService != null) {
            log.info("[Standalone模式] 使用流式分发服务(意图识别+业务分发), requestId: {}", request.getRequestId());

            try {
                redisTemplate.opsForValue().set("chat:request:" + request.getRequestId(),
                    objectMapper.writeValueAsString(request), RESULT_TTL);

                ChatResultMessage processingResult = ChatResultMessage.builder()
                        .requestId(request.getRequestId())
                        .memoryId(request.getMemoryId())
                        .status(ChatResultMessage.ResultStatus.PROCESSING)
                        .build();
                cacheResult(request.getRequestId(), processingResult);

                Flux<String> flux = streamingDispatchService.chat(request.getMemoryId(), request.getMessage());

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
        } else if (streamingChatService != null) {
            log.info("[Standalone模式] 使用原始流式处理(无意图识别), requestId: {}", request.getRequestId());

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
        if (!redisHealthIndicator.isRedisAvailable()) {
            log.warn("Redis不可用，跳过缓存结果, requestId: {}", requestId);
            return;
        }
        
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(RESULT_CACHE_PREFIX + requestId, jsonResult, RESULT_TTL);
        } catch (Exception e) {
            log.error("缓存结果失败, requestId: {}", requestId, e);
            redisHealthIndicator.markRedisUnavailable();
        }
    }
    
    private void updateStreamContent(String requestId, String content) {
        if (!redisHealthIndicator.isRedisAvailable()) {
            log.warn("Redis不可用，跳过更新流式内容, requestId: {}", requestId);
            return;
        }
        
        try {
            String cacheKey = STREAM_CACHE_PREFIX + requestId;
            String existingContent = redisTemplate.opsForValue().get(cacheKey);
            String newContent = (existingContent != null ? existingContent : "") + content;
            redisTemplate.opsForValue().set(cacheKey, newContent, RESULT_TTL);
        } catch (Exception e) {
            log.error("更新流式内容失败, requestId: {}", requestId, e);
            redisHealthIndicator.markRedisUnavailable();
        }
    }
    
    private String getStreamContent(String requestId) {
        if (!redisHealthIndicator.isRedisAvailable()) {
            log.warn("Redis不可用，返回空流式内容, requestId: {}", requestId);
            return "";
        }
        
        try {
            String cacheKey = STREAM_CACHE_PREFIX + requestId;
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.error("获取流式内容失败, requestId: {}", requestId, e);
            redisHealthIndicator.markRedisUnavailable();
            return "";
        }
    }
    
    /**
     * 安全地获取Redis值
     */
    private String safeGetRedisValue(String key) {
        if (!redisHealthIndicator.isRedisAvailable()) {
            return null;
        }
        
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取Redis值失败, key: {}", key, e);
            redisHealthIndicator.markRedisUnavailable();
            return null;
        }
    }
    
    /**
     * 安全地设置Redis值
     */
    private boolean safeSetRedisValue(String key, String value, Duration timeout) {
        if (!redisHealthIndicator.isRedisAvailable()) {
            return false;
        }
        
        try {
            redisTemplate.opsForValue().set(key, value, timeout);
            return true;
        } catch (Exception e) {
            log.error("设置Redis值失败, key: {}", key, e);
            redisHealthIndicator.markRedisUnavailable();
            return false;
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
            
            String resultJson = safeGetRedisValue(cacheKey);
            
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
            sendSseEvent(emitter, "error", Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("SSE超时回调, requestId: {}", requestId);
            sendSseEvent(emitter, "timeout", Map.of("status", "timeout"));
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
            String resultJson = safeGetRedisValue(cacheKey);
            
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
            
            String requestJson = safeGetRedisValue("chat:request:" + requestId);
            ChatRequestMessage request = null;
            
            if (requestJson != null) {
                try {
                    request = objectMapper.readValue(requestJson, ChatRequestMessage.class);
                } catch (Exception e) {
                    log.error("解析请求数据失败, requestId: {}", requestId, e);
                }
            }
            
            // 从内存缓存中获取请求数据（当Redis不可用时）
            if (request == null) {
                request = memoryCache.get(requestId);
                if (request == null) {
                    log.warn("请求数据不存在, requestId: {}", requestId);
                    sendSseEvent(emitter, "error", Map.of(
                        "status", "error",
                        "message", "请求数据不存在，请重新发送消息"
                    ));
                    emitter.complete();
                    sseConnections.remove(requestId);
                    return emitter;
                }
                log.info("从内存缓存获取请求数据, requestId: {}", requestId);
            }

            if (request != null && streamingDispatchService != null) {
                log.info("使用流式分发服务(意图识别+业务分发), requestId: {}", requestId);
                safeSetRedisValue(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);

                final String finalRequestId = requestId;
                final SseEmitter finalEmitter = emitter;
                final String finalCacheKey = cacheKey;
                final ChatRequestMessage finalRequest = request;

                Flux<String> flux = streamingDispatchService.chat(request.getMemoryId(), request.getMessage());

                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());

flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);

                            safeSetRedisValue(
                                STREAM_CACHE_PREFIX + finalRequestId,
                                updated,
                                RESULT_TTL
                            );

                            sendSseEvent(finalEmitter, "chunk", Map.of(
                                "content", chunk,
                                "status", "streaming",
                                "fullContent", updated,
                                "progress", 50
                            ));

                            log.debug("流式内容块, requestId: {}, chunk长度: {}, 累计长度: {}",
                                finalRequestId, chunk.length(), updated.length());
                        } catch (Exception ex) {
                            log.error("发送流式内容块失败, requestId: {}", finalRequestId, ex);
                        }
                    })
                    .doOnComplete(() -> {
                        long processingTime = System.currentTimeMillis() - startTime.get();
                        log.info("流式处理完成, requestId: {}, 总长度: {}, 耗时: {}ms",
                            finalRequestId, accumulated.get().length(), processingTime);

                        String finalContent = accumulated.get();

                        // 保存聊天信息到数据库
                        saveChatToDatabase(finalRequest.getMemoryId(), finalRequest.getMessage(), finalContent);

                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(finalRequestId)
                            .memoryId(finalRequest.getMemoryId())
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();

                        try {
                            String jsonResult = objectMapper.writeValueAsString(finalResult);
                            safeSetRedisValue(finalCacheKey, jsonResult, RESULT_TTL);

                            processResultEvent(finalEmitter, finalRequestId, finalResult);
                            finalEmitter.complete();
                        } catch (Exception ex) {
                            log.error("完成流式处理失败, requestId: {}", finalRequestId, ex);
                            finalEmitter.completeWithError(ex);
                        }
                        sseConnections.remove(finalRequestId);
                    })
                    .doOnError(error -> {
                        log.error("流式处理错误, requestId: {}", finalRequestId, error);
                        sendSseEvent(finalEmitter, "error", Map.of(
                            "status", "error",
                            "message", error.getMessage()
                        ));
                        finalEmitter.completeWithError(error);
                        sseConnections.remove(finalRequestId);
                    })
                    .subscribe();
            } else if (request != null && streamingChatService != null) {
                log.info("使用原始流式处理(无意图识别), requestId: {}", requestId);
                safeSetRedisValue(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);

                final String finalRequestId = requestId;
                final SseEmitter finalEmitter = emitter;
                final String finalCacheKey = cacheKey;
                final ChatRequestMessage finalRequest = request;

                Flux<String> flux = streamingChatService.chat(request.getMemoryId(), request.getMessage());

                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());

flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);

                            safeSetRedisValue(
                                STREAM_CACHE_PREFIX + finalRequestId,
                                updated,
                                RESULT_TTL
                            );

                            sendSseEvent(finalEmitter, "chunk", Map.of(
                                "content", chunk,
                                "status", "streaming",
                                "fullContent", updated,
                                "progress", 50
                            ));

                            log.debug("流式内容块, requestId: {}, chunk长度: {}, 累计长度: {}",
                                finalRequestId, chunk.length(), updated.length());
                        } catch (Exception ex) {
                            log.error("发送流式内容块失败, requestId: {}", finalRequestId, ex);
                        }
                    })
                    .doOnComplete(() -> {
                        long processingTime = System.currentTimeMillis() - startTime.get();
                        log.info("流式处理完成, requestId: {}, 总长度: {}, 耗时: {}ms",
                            finalRequestId, accumulated.get().length(), processingTime);

                        String finalContent = accumulated.get();

                        // 保存聊天信息到数据库
                        saveChatToDatabase(finalRequest.getMemoryId(), finalRequest.getMessage(), finalContent);

                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(finalRequestId)
                            .memoryId(finalRequest.getMemoryId())
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();

                        try {
                            String jsonResult = objectMapper.writeValueAsString(finalResult);
                            safeSetRedisValue(finalCacheKey, jsonResult, RESULT_TTL);

                            processResultEvent(finalEmitter, finalRequestId, finalResult);
                            finalEmitter.complete();
                        } catch (Exception ex) {
                            log.error("完成流式处理失败, requestId: {}", finalRequestId, ex);
                            finalEmitter.completeWithError(ex);
                        }
                        sseConnections.remove(finalRequestId);
                    })
                    .doOnError(error -> {
                        log.error("流式处理错误, requestId: {}", finalRequestId, error);
                        sendSseEvent(finalEmitter, "error", Map.of(
                            "status", "error",
                            "message", error.getMessage()
                        ));
                        finalEmitter.completeWithError(error);
                        sseConnections.remove(finalRequestId);
                    })
                    .subscribe();
            } else if (request != null) {
                log.info("流式服务不可用，使用普通模式, requestId: {}", requestId);
                
                sendSseEvent(emitter, "warning", Map.of(
                    "status", "warning",
                    "message", "流式服务不可用，将使用普通模式处理"
                ));
                
                final String finalRequestId = requestId;
                final SseEmitter finalEmitter = emitter;
                final ChatRequestMessage finalRequest = request;
                
                ChatResultMessage processingResult = ChatResultMessage.builder()
                        .requestId(requestId)
                        .memoryId(request.getMemoryId())
                        .status(ChatResultMessage.ResultStatus.PROCESSING)
                        .build();
                cacheResult(requestId, processingResult);
                
                CompletableFuture.runAsync(() -> {
                    try {
                        long startTimeMs = System.currentTimeMillis();
                        String result = chatService.chat(finalRequest.getMemoryId(), finalRequest.getMessage());
                        long processingTime = System.currentTimeMillis() - startTimeMs;
                        
                        updateStreamContent(finalRequestId, result);
                        
                        ChatResultMessage finalResult = ChatResultMessage.builder()
                                .requestId(finalRequestId)
                                .memoryId(finalRequest.getMemoryId())
                                .result(result)
                                .status(ChatResultMessage.ResultStatus.SUCCESS)
                                .processingTimeMs(processingTime)
                                .build();
                        
                        cacheResult(finalRequestId, finalResult);
                        
                        sendSseEvent(finalEmitter, "chunk", Map.of(
                            "content", result,
                            "status", "streaming",
                            "fullContent", result,
                            "progress", 100
                        ));
                        
                        processResultEvent(finalEmitter, finalRequestId, finalResult);
                        finalEmitter.complete();
                        sseConnections.remove(finalRequestId);
                        
                        log.info("普通模式处理完成, requestId: {}, 耗时: {}ms", finalRequestId, processingTime);
                    } catch (Exception ex) {
                        log.error("普通模式处理失败, requestId: {}", finalRequestId, ex);
                        ChatResultMessage failedResult = ChatResultMessage.builder()
                                .requestId(finalRequestId)
                                .memoryId(finalRequest.getMemoryId())
                                .status(ChatResultMessage.ResultStatus.FAILED)
                                .errorMessage(ex.getMessage())
                                .build();
                        cacheResult(finalRequestId, failedResult);
                        
                        sendSseEvent(finalEmitter, "error", Map.of(
                            "status", "error",
                            "message", ex.getMessage()
                        ));
                        finalEmitter.completeWithError(ex);
                        sseConnections.remove(finalRequestId);
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
            sendSseEvent(emitter, "error", Map.of(
                "status", "error",
                "message", "SSE流初始化失败: " + e.getMessage()
            ));
            emitter.complete();
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("SSE流超时, requestId: {}", requestId);
            sendSseEvent(emitter, "timeout", Map.of("status", "timeout", "message", "处理超时"));
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
                    sendSseEvent(emitter, "error", Map.of("status", "error", "message", e.getMessage()));
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
            sendSseEvent(emitter, "timeout", Map.of("status", "timeout"));
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
    
    private void processResultEvent(SseEmitter emitter, String requestId, ChatResultMessage result) {
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
    
    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            if (emitter != null) {
                String jsonData = objectMapper.writeValueAsString(data);
                // 明确指定内容类型为 application/json，避免消息转换错误
                emitter.send(SseEmitter.event().name(eventName).data(jsonData, MediaType.APPLICATION_JSON));
            }
        } catch (Exception e) {
            log.debug("发送SSE事件失败，连接可能已关闭", e);
        }
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
    
    @GetMapping(value = "/sse/count-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getSseCountStream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("activeConnections", sseConnections.size());
            response.put("timestamp", System.currentTimeMillis());
            
            sendSseEvent(emitter, "count", response);
            emitter.complete();
        } catch (Exception e) {
            log.error("获取SSE连接数失败", e);
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
    
    @PostMapping("/chat/http-stream")
    @Operation(summary = "HTTP流式聊天", description = "通过POST发送消息，SSE流式返回结果")
    public Map<String, Object> startHttpStreamChat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        java.util.List<String> extractedTexts = chatForm.getExtractedTexts();

        log.info("收到HTTP流式聊天请求, memoryId: {}, message: {}", memoryId, userMessage);
        if (extractedTexts != null && !extractedTexts.isEmpty()) {
            log.info("附带文件提取内容数量: {}", extractedTexts.size());
        }

        String fullMessage = buildFullMessage(userMessage, extractedTexts);
        String requestId = "http_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();

        try {
            ChatRequestMessage request = ChatRequestMessage.create(memoryId, fullMessage);
            String requestJson = objectMapper.writeValueAsString(request);
            boolean redisSaved = safeSetRedisValue("chat:request:" + requestId, requestJson, RESULT_TTL);
            safeSetRedisValue(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);
            
            // 当Redis保存失败时，使用内存缓存作为降级方案
            if (!redisSaved) {
                log.warn("Redis保存失败，使用内存缓存, requestId: {}", requestId);
                memoryCache.put(requestId, request);
                // 设置内存缓存过期时间（10分钟）
                new Thread(() -> {
                    try {
                        Thread.sleep(10 * 60 * 1000);
                        memoryCache.remove(requestId);
                        log.info("内存缓存已过期并清理, requestId: {}", requestId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        } catch (Exception e) {
            log.error("保存请求数据失败, requestId: {}", requestId, e);
            // 异常时也使用内存缓存作为降级方案
            try {
                ChatRequestMessage request = ChatRequestMessage.create(memoryId, fullMessage);
                memoryCache.put(requestId, request);
                log.warn("使用内存缓存作为降级方案, requestId: {}", requestId);
                // 设置内存缓存过期时间（10分钟）
                new Thread(() -> {
                    try {
                        Thread.sleep(10 * 60 * 1000);
                        memoryCache.remove(requestId);
                        log.info("内存缓存已过期并清理, requestId: {}", requestId);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } catch (Exception ex) {
                log.error("创建请求消息失败, requestId: {}", requestId, ex);
            }
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
            
            String requestJson = safeGetRedisValue("chat:request:" + requestId);
            ChatRequestMessage request = null;
            Long tempMemoryId = null;
            String tempMessage = null;
            
            if (requestJson == null) {
                log.warn("请求数据不存在或Redis不可用, requestId: {}", requestId);
                // 尝试从内存缓存中获取
                request = memoryCache.get(requestId);
                if (request != null) {
                    log.info("从内存缓存中获取请求数据, requestId: {}", requestId);
                    tempMemoryId = request.getMemoryId();
                    tempMessage = request.getMessage();
                } else {
                    // 内存缓存也没有时，使用降级处理
                    log.warn("内存缓存也不存在，使用降级处理, requestId: {}", requestId);
                    tempMemoryId = System.currentTimeMillis();
                    tempMessage = "系统服务暂时不可用，请稍后重试";
                    log.info("使用降级模式, memoryId: {}, message: {}", tempMemoryId, tempMessage);
                }
            } else {
                try {
                    request = objectMapper.readValue(requestJson, ChatRequestMessage.class);
                    tempMemoryId = request.getMemoryId();
                    tempMessage = request.getMessage();
                } catch (Exception e) {
                    log.error("解析请求数据失败, requestId: {}", requestId, e);
                    // 解析失败时，尝试从内存缓存中获取
                    request = memoryCache.get(requestId);
                    if (request != null) {
                        log.info("解析失败，从内存缓存中获取请求数据, requestId: {}", requestId);
                        tempMemoryId = request.getMemoryId();
                        tempMessage = request.getMessage();
                    } else {
                        // 内存缓存也没有时，使用降级处理
                        tempMemoryId = System.currentTimeMillis();
                        tempMessage = "请求数据解析失败，请稍后重试";
                        log.info("使用降级模式, memoryId: {}, message: {}", tempMemoryId, tempMessage);
                    }
                }
            }
            
            // 从内存缓存中获取后，清理内存缓存，避免内存泄漏
            if (request != null) {
                memoryCache.remove(requestId);
                log.debug("清理内存缓存, requestId: {}", requestId);
            }
            
            // 声明为final变量，供lambda表达式使用
            final Long memoryId = tempMemoryId;
            final String message = tempMessage;

if (streamingDispatchService != null) {
                log.info("使用流式分发服务(意图识别+业务分发)处理HTTP请求, requestId: {}", requestId);

                // 安全设置Redis值
                safeSetRedisValue(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);

                Flux<String> flux = streamingDispatchService.chat(memoryId, message);

                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());

                flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);

                            // 安全更新流式内容
                            updateStreamContent(requestId, chunk);

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

                        // 保存聊天信息到数据库
                        saveChatToDatabase(memoryId, message, finalContent);

                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(requestId)
                            .memoryId(memoryId)
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();

                        try {
                            // 安全缓存结果
                            cacheResult(requestId, finalResult);

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
                        sendSseEvent(emitter, "error", Map.of(
                            "status", "error",
                            "message", error.getMessage()
                        ));
                        emitter.completeWithError(error);
                        sseConnections.remove(requestId);
                    })
                    .subscribe();
} else if (streamingChatService != null) {
                log.info("使用原始流式服务处理HTTP请求(无意图识别), requestId: {}", requestId);

                // 安全设置Redis值
                safeSetRedisValue(STREAM_CACHE_PREFIX + requestId, "", RESULT_TTL);

                Flux<String> flux = streamingChatService.chat(memoryId, message);

                AtomicReference<String> accumulated = new AtomicReference<>("");
                AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());

flux.publishOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        try {
                            String current = accumulated.get();
                            String updated = current + chunk;
                            accumulated.set(updated);

                            // 安全更新流式内容
                            updateStreamContent(requestId, chunk);

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

                        // 保存聊天信息到数据库
                        saveChatToDatabase(memoryId, message, finalContent);

                        ChatResultMessage finalResult = ChatResultMessage.builder()
                            .requestId(requestId)
                            .memoryId(memoryId)
                            .result(finalContent)
                            .status(ChatResultMessage.ResultStatus.SUCCESS)
                            .processingTimeMs(processingTime)
                            .build();

                        try {
                            // 安全缓存结果
                            cacheResult(requestId, finalResult);

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
                        sendSseEvent(emitter, "error", Map.of(
                            "status", "error",
                            "message", error.getMessage()
                        ));
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
                        emitter.completeWithError(ex);
                        sseConnections.remove(requestId);
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("HTTP流式初始化异常, requestId: {}", requestId, e);
            sendSseEvent(emitter, "error", Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
            emitter.completeWithError(e);
            sseConnections.remove(requestId);
        }
        
        emitter.onTimeout(() -> {
            log.warn("HTTP流式超时, requestId: {}", requestId);
            sendSseEvent(emitter, "timeout", Map.of("status", "timeout", "message", "处理超时"));
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
    
    /**
     * 保存聊天信息到数据库
     * @param memoryId 对话对应的memoryId
     * @param userMessage 用户消息
     * @param aiResponse AI回复内容
     */
    private void saveChatToDatabase(Long memoryId, String userMessage, String aiResponse) {
        if (chatSaveService == null) {
            log.warn("ChatSaveService未注入，跳过数据库保存");
        }
        
        try {
            chatSaveService.saveChatInfo(memoryId, userMessage, "general", aiResponse);
            log.info("流式聊天记录已保存到数据库, memoryId: {}", memoryId);
        } catch (Exception e) {
            log.error("保存流式聊天记录失败, memoryId: {}", memoryId, e);
        }
        
        if (mongoChatMemoryStore == null) {
            log.warn("MongoChatMemoryStore未注入，跳过MongoDB保存");
            return;
        }
        
        try {
            List<ChatMessage> messages = new java.util.ArrayList<>();
            messages.add(UserMessage.from(userMessage));
            messages.add(AiMessage.from(aiResponse));
            mongoChatMemoryStore.updateMessages(memoryId, messages);
            log.info("流式聊天记录已保存到MongoDB, memoryId: {}", memoryId);
        } catch (Exception e) {
            log.error("保存流式聊天记录到MongoDB失败, memoryId: {}", memoryId, e);
        }
    }
}
