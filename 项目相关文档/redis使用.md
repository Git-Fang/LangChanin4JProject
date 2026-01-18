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

在SSE流式输出过程中，累加各个内容片段，支持断点续传和内容查询。

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

## Redis操作封装

**配置类**: `RedisConfig.java`

```java
@Configuration
public class RedisConfig {
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

# 删除过期数据
DEL chat:result:{requestId}
DEL chat:stream:{requestId}
```

### 清理策略

- **TTL自动过期**：所有缓存数据24小时后自动过期
- **手动清理**：处理完成后由消费者主动删除流式内容
- **建议定期执行**：如需手动清理，可使用 `redis-cli --scan --pattern "chat:*" | xargs DEL`

## 注意事项

1. **数据一致性**：Redis仅作为缓存，不作为持久化存储，原始数据存储在MongoDB
2. **内存管理**：建议生产环境配置 `maxmemory` 和淘汰策略
3. **键命名规范**：统一使用 `{业务}:{类型}:{ID}` 格式
4. **TTL设置**：当前统一为24小时，可根据业务需求调整
