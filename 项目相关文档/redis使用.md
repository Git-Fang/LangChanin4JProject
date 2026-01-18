# Redis使用文档

## 概述

本项目使用Redis作为分布式缓存和消息中间件，主要用于聊天请求/结果的缓存管理以及流式输出的内容累加。

## 连接配置

```yaml
spring:
  redis:
    host: ${SPRING_REDIS_HOST:localhost}  # Docker环境默认为 redis
    port: ${SPRING_REDIS_PORT:6379}
```

Docker部署时，通过 `docker-compose-kafka.yml` 中的 `SPRING_REDIS_HOST: redis` 环境变量连接。

## 核心用途

### 1. 聊天请求缓存

存储异步聊天请求的完整信息，用于后续查询和重试。

| 用途 | Key模式 | TTL |
|------|---------|-----|
| 请求消息缓存 | `chat:request:{requestId}` | 24小时 |

**代码位置**: `AsyncChatController.java:104`

```java
redisTemplate.opsForValue().set("chat:request:" + request.getRequestId(), requestJson, RESULT_TTL);
```

---

### 2. 聊天结果缓存

缓存聊天处理结果，支持结果查询接口。

| 用途 | Key模式 | TTL |
|------|---------|-----|
| 处理结果缓存 | `chat:result:{requestId}` | 24小时 |

**代码位置**: `ChatRequestConsumer.java:189`

```java
private void cacheResult(String requestId, ChatResultMessage result) {
    String cacheKey = RESULT_CACHE_PREFIX + requestId;
    String jsonResult = objectMapper.writeValueAsString(result);
    redisTemplate.opsForValue().set(cacheKey, jsonResult, RESULT_TTL);
}
```

---

### 3. 流式输出内容累加

在SSE流式输出过程中，累加各个内容片段，支持断点续传。

| 用途 | Key模式 | TTL |
|------|---------|-----|
| 流式内容累加 | `chat:stream:{requestId}` | 24小时 |

**代码位置**: `ChatRequestConsumer.java:196-206`

```java
private void updateStreamContent(String requestId, String content) {
    String cacheKey = STREAM_CACHE_PREFIX + requestId;
    String existingContent = redisTemplate.opsForValue().get(cacheKey);
    String newContent = (existingContent != null ? existingContent : "") + content;
    redisTemplate.opsForValue().set(cacheKey, newContent, RESULT_TTL);
}

private void clearStreamContent(String requestId) {
    String cacheKey = STREAM_CACHE_PREFIX + requestId;
    redisTemplate.delete(cacheKey);
}
```

---

## Redis数据结构

所有数据均使用 **String** 类型，存储JSON序列化后的字符串：

```
┌─────────────────────────────────────────────────────────────┐
│  Key: chat:request:{requestId}                              │
│  Value: {"memoryId": 123, "message": "xxx", ...}           │
│  TTL: 24h                                                   │
├─────────────────────────────────────────────────────────────┤
│  Key: chat:result:{requestId}                               │
│  Value: {"requestId": "xxx", "status": "SUCCESS", ...}     │
│  TTL: 24h                                                   │
├─────────────────────────────────────────────────────────────┤
│  Key: chat:stream:{requestId}                               │
│  Value: "完整的流式输出内容..."                              │
│  TTL: 24h                                                   │
└─────────────────────────────────────────────────────────────┘
```

## 常量定义

```java
// AsyncChatController.java & ChatRequestConsumer.java
private static final String RESULT_CACHE_PREFIX = "chat:result:";
private static final String STREAM_CACHE_PREFIX = "chat:stream:";
private static final Duration RESULT_TTL = Duration.ofHours(24);
```

---

## Kafka与Redis交互

### 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Kafka-Redis 交互架构                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                   │
│   │   客户端      │────▶│  API接口     │────▶│   Redis      │                   │
│   │              │     │              │     │ 缓存请求数据   │                   │
│   └──────────────┘     └──────────────┘     └──────────────┘                   │
│                                                  │                               │
│                                                  ▼                               │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                   │
│   │   客户端      │◀────│  SSE接口     │◀────│   Redis      │                   │
│   │              │     │              │     │ 读取缓存内容   │                   │
│   └──────────────┘     └──────────────┘     └──────────────┘                   │
│                                                                                 │
│                        ┌──────────────┐                                         │
│                        │   Kafka      │                                         │
│                        │  请求队列     │                                         │
│                        └──────────────┘                                         │
│                              ▲                                                 │
│                              │                                                 │
│   ┌──────────────┐     ┌──────────────┐                                         │
│   │   Redis      │     │  生产者      │                                         │
│   │ 缓存请求数据   │────▶│  发送请求    │                                         │
│   └──────────────┘     └──────────────┘                                         │
│                              │                                                 │
│                              ▼                                                 │
│                        ┌──────────────┐                                         │
│                        │  消费者      │                                         │
│                        │  消费消息     │                                         │
│                        └──────────────┘                                         │
│                              │                                                 │
│                              ▼                                                 │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                   │
│   │   Redis      │◀────│  业务处理    │────▶│   Kafka      │                   │
│   │ 缓存处理结果   │     │  意图识别    │     │  结果队列     │                   │
│   └──────────────┘     └──────────────┘     └──────────────┘                   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Kafka Topics

| Topic名称 | 用途 | 消息类型 |
|-----------|------|----------|
| `ai-chat-request` | AI聊天请求队列 | `ChatRequestMessage` |
| `ai-chat-result` | AI处理结果队列 | `ChatResultMessage` |

### 交互流程详解

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           Kafka-Redis 完整交互流程                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  1. 请求发起与缓存                                                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  客户端                                                                      │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  POST /xiaozhi/chat/async                                                  │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  AsyncChatController                                                        │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  redisTemplate.opsForValue().set("chat:request:{requestId}",               │    │
│  │      requestJson, RESULT_TTL)  ←── 缓存请求消息                              │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatRequestProducer.sendRequest()                                          │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  Kafka:ai-chat-request  ←── 生产请求消息                                     │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  2. 消息消费与处理                                                                │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  Kafka Consumer (ChatRequestConsumer)                                      │    │
│  │  @KafkaListener(topics = "ai-chat-request")                                │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  clearStreamContent(requestId)  ←── 清理旧流式内容                           │    │
│  │  redisTemplate.delete("chat:stream:{requestId}")                           │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatResultMessage processing = PROCESSING状态                               │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  cacheResult(requestId, processing)  ←── 缓存处理中状态                       │    │
│  │  redisTemplate.opsForValue().set("chat:result:{requestId}",                │    │
│  │      processingJson, RESULT_TTL)                                            │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  CompletableFuture.supplyAsync(() -> processRequest())                      │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  processRequest()  ←── 异步业务处理                                           │    │
│  │    ├── intentRecognition()  ←── 意图识别                                     │    │
│  │    └── routeToService()  ←── 路由到具体服务                                   │    │
│  │         ├── doctorAgent.chat()  ←── 医疗咨询                                 │    │
│  │         ├── translaterService.translate()  ←── 翻译                          │    │
│  │         ├── termExtractionAgent.chat()  ←── 术语提取                         │    │
│  │         └── nl2SQLService.execute()  ←── 自然语言查询                         │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatResultMessage result = SUCCESS状态                                      │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  cacheResult(requestId, result)  ←── 缓存处理结果                             │    │
│  │  redisTemplate.opsForValue().set("chat:result:{requestId}",                │    │
│  │      resultJson, RESULT_TTL)                                                │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  updateStreamContent(requestId, resultContent)  ←── 累加流式内容              │    │
│  │  String existing = redisTemplate.opsForValue().get("chat:stream:{id}")     │    │
│  │  redisTemplate.opsForValue().set("chat:stream:{id}",                       │    │
│  │      existing + newContent, RESULT_TTL)                                     │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatRequestProducer.sendResult()                                           │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  Kafka:ai-chat-result  ←── 生产结果消息                                      │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                    │                                            │
│                                    ▼                                            │
│  3. 异常处理与失败状态                                                            │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │  异常捕获                                                                   │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatResultMessage failed = FAILED状态                                       │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  cacheResult(requestId, failed)  ←── 缓存失败结果                             │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  ChatRequestProducer.sendFailedStatus()                                     │    │
│  │    │                                                                        │    │
│  │    ▼                                                                        │    │
│  │  Kafka:ai-chat-result  ←── 生产失败状态                                      │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 核心代码：Kafka消费者中的Redis操作

**文件**: `ChatRequestConsumer.java`

```java
@Slf4j
@Service
@Profile("!standalone")
@RequiredArgsConstructor
public class ChatRequestConsumer {
    
    private final StringRedisTemplate redisTemplate;
    private final ChatRequestProducer requestProducer;
    private final ObjectMapper objectMapper;
    
    private static final String RESULT_CACHE_PREFIX = "chat:result:";
    private static final String STREAM_CACHE_PREFIX = "chat:stream:";
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    
    @KafkaListener(topics = "ai-chat-request", groupId = "ai-request-consumer")
    public void consumeChatRequest(ConsumerRecord<String, ChatRequestMessage> record, Acknowledgment ack) {
        ChatRequestMessage request = record.value();
        
        try {
            // 1. 清理流式内容（新的请求开始）
            clearStreamContent(request.getRequestId());
            
            // 2. 缓存处理中状态
            ChatResultMessage processingResult = ChatResultMessage.builder()
                    .requestId(request.getRequestId())
                    .memoryId(request.getMemoryId())
                    .status(ChatResultMessage.ResultStatus.PROCESSING)
                    .processedAt(LocalDateTime.now())
                    .build();
            cacheResult(request.getRequestId(), processingResult);
            
            // 3. 异步处理请求
            CompletableFuture.supplyAsync(() -> processRequest(request), mdcExecutorService)
                    .thenAccept(result -> {
                        // 4. 缓存处理结果
                        cacheResult(request.getRequestId(), result);
                        
                        // 5. 累加流式内容
                        updateStreamContent(request.getRequestId(), result.getResult());
                        
                        // 6. 发送结果到Kafka
                        requestProducer.sendResult(result);
                        
                        ack.acknowledge();
                    })
                    .exceptionally(ex -> {
                        // 异常处理：缓存失败结果并发送失败状态
                        ChatResultMessage failedResult = ChatResultMessage.builder()
                                .requestId(request.getRequestId())
                                .memoryId(request.getMemoryId())
                                .status(ChatResultMessage.ResultStatus.FAILED)
                                .errorMessage(ex.getMessage())
                                .processedAt(LocalDateTime.now())
                                .build();
                        cacheResult(request.getRequestId(), failedResult);
                        requestProducer.sendFailedStatus(request.getRequestId(), request.getMemoryId(), ex.getMessage());
                        ack.acknowledge();
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("消费消息异常", e);
            requestProducer.sendFailedStatus(request.getRequestId(), request.getMemoryId(), e.getMessage());
            ack.acknowledge();
        }
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
    
    private void updateStreamContent(String requestId, String content) {
        String cacheKey = STREAM_CACHE_PREFIX + requestId;
        try {
            String existingContent = redisTemplate.opsForValue().get(cacheKey);
            String newContent = (existingContent != null ? existingContent : "") + content;
            redisTemplate.opsForValue().set(cacheKey, newContent, RESULT_TTL);
        } catch (Exception e) {
            log.error("更新流式内容失败, requestId: {}", requestId, e);
        }
    }
    
    private void clearStreamContent(String requestId) {
        String cacheKey = STREAM_CACHE_PREFIX + requestId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.error("清理流式内容失败, requestId: {}", requestId, e);
        }
    }
}
```

### 消息生产者：Kafka发送结果

**文件**: `ChatRequestProducer.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRequestProducer {
    
    private final KafkaTemplate<String, ChatRequestMessage> requestKafkaTemplate;
    private final KafkaTemplate<String, ChatResultMessage> resultKafkaTemplate;
    
    private static final String AI_REQUEST_TOPIC = "ai-chat-request";
    private static final String AI_RESULT_TOPIC = "ai-chat-result";
    
    // 发送请求到Kafka
    public CompletableFuture<SendResult<String, ChatRequestMessage>> sendRequest(ChatRequestMessage request) {
        return requestKafkaTemplate.send(AI_REQUEST_TOPIC, request.getRequestId(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("请求发送成功, requestId: {}", request.getRequestId());
                    } else {
                        log.error("请求发送失败, requestId: {}", request.getRequestId(), ex);
                    }
                });
    }
    
    // 发送处理结果到Kafka
    public CompletableFuture<SendResult<String, ChatResultMessage>> sendResult(ChatResultMessage result) {
        return resultKafkaTemplate.send(AI_RESULT_TOPIC, result.getRequestId(), result)
                .whenComplete((sendResult, ex) -> {
                    if (ex == null) {
                        log.info("结果发送成功, requestId: {}", result.getRequestId());
                    } else {
                        log.error("结果发送失败, requestId: {}", result.getRequestId(), ex);
                    }
                });
    }
    
    // 发送失败状态
    public void sendFailedStatus(String requestId, Long memoryId, String errorMessage) {
        ChatResultMessage failedResult = ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ChatResultMessage.ResultStatus.FAILED)
                .errorMessage(errorMessage)
                .processedAt(LocalDateTime.now())
                .build();
        sendResult(failedResult);
    }
}
```

### 断点续传支持

Redis在SSE流式输出中实现断点续传功能：

```java
// AsyncChatController.java - SSE流式查询
@GetMapping(value = "/chat/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChatResult(@PathVariable String requestId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
    
    CompletableFuture.runAsync(() -> {
        try {
            // 1. 从Redis读取已缓存的流式内容
            String cacheKey = STREAM_CACHE_PREFIX + requestId;
            String existingContent = redisTemplate.opsForValue().get(cacheKey);
            
            // 2. 发送已缓存的内容（断点续传）
            if (existingContent != null && !existingContent.isEmpty()) {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(existingContent));
            }
            
            // 3. 订阅Kafka消息，实时推送新内容...
            
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}
```

---

## 业务场景

### 异步聊天流程

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   客户端      │───▶│  API接口     │───▶│   Redis      │
│              │    │ /async/chat  │    │ 缓存请求      │
└──────────────┘    └──────────────┘    └──────────────┘
                                                   │
                                                   ▼
                     ┌──────────────┐    ┌──────────────┐
                     │   Kafka      │◀───│  请求入队     │
                     │              │    │              │
                     └──────────────┘    └──────────────┘
                                                   │
                                                   ▼
                     ┌──────────────┐    ┌──────────────┐
                     │  消费者处理   │───▶│   Redis      │
                     │              │    │ 缓存结果/内容 │
                     └──────────────┘    └──────────────┘
                                                   │
                                                   ▼
                     ┌──────────────┐    ┌──────────────┐
                     │  SSE推送     │───▶│   Redis      │
                     │  断点续传    │    │ 读取累加内容  │
                     └──────────────┘    └──────────────┘
```

### 结果查询

客户端可通过 `requestId` 查询处理结果：

```bash
# 获取完整结果
GET /xiaozhi/result/{requestId}

# 获取流式内容（支持断点续传）
GET /xiaozhi/chat/stream/{requestId}
```

---

## Redis操作封装

**配置类**: `RedisConfig.java`

```java
@Configuration
public class RedisConfig {
    @Value("${SPRING_REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${SPRING_REDIS_PORT:6379}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

---

## 监控和维护

### Docker环境检查

```bash
# 查看Redis容器状态
docker ps | grep redis

# 进入Redis CLI
docker exec -it redis redis-cli

# 查看所有键
KEYS chat:*

# 查看特定请求结果
GET chat:result:{requestId}

# 查看流式内容
GET chat:stream:{requestId}

# 查看请求数据
GET chat:request:{requestId}

# 删除过期数据
DEL chat:result:{requestId}
DEL chat:stream:{requestId}
DEL chat:request:{requestId}
```

### 清理策略

- **TTL自动过期**：所有缓存数据24小时后自动过期
- **手动清理**：处理完成后由消费者主动删除流式内容
- **建议定期执行**：如需手动清理，可使用 `redis-cli --scan --pattern "chat:*" | xargs DEL`

---

## 注意事项

1. **数据一致性**：Redis仅作为缓存，不作为持久化存储，原始数据存储在MongoDB
2. **内存管理**：建议生产环境配置 `maxmemory` 和淘汰策略
3. **键命名规范**：统一使用 `{业务}:{类型}:{ID}` 格式
4. **TTL设置**：当前统一为24小时，可根据业务需求调整
5. **Kafka消费者**：使用 `CompletableFuture` 异步处理，不阻塞消费线程
6. **断点续传**：流式内容累加到Redis，支持客户端中断后重新连接获取完整内容
