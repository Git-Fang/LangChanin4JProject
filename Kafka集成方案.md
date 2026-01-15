# Kafka集成方案：提高AI请求处理速率

## 文档版本与修订历史

| 版本 | 日期       | 修订内容               | 作者         |
| :--- | :--------- | :--------------------- | :----------- |
| v1.0 | 2026-01-15 | 初始版本，完整方案设计 | AI Assistant |

------

## 目录

1. [方案概述](http://127.0.0.1:4096/app#1-方案概述)
2. [现状分析](http://127.0.0.1:4096/app#2-现状分析)
3. [架构设计](http://127.0.0.1:4096/app#3-架构设计)
4. [实现方案](http://127.0.0.1:4096/app#4-实现方案)
5. [性能优化](http://127.0.0.1:4096/app#5-性能优化)
6. [部署方案](http://127.0.0.1:4096/app#6-部署方案)
7. [迁移路径](http://127.0.0.1:4096/app#7-迁移路径)
8. [预期效果](http://127.0.0.1:4096/app#8-预期效果)
9. [风险与应对](http://127.0.0.1:4096/app#9-风险与应对)
10. [实施计划](http://127.0.0.1:4096/app#10-实施计划)

------

## 1. 方案概述

### 1.1 背景与目标

本项目（RAGTranslation4）是一个基于Spring Boot 3.5.0 + LangChain4j 1.5.0的AI对话系统，当前采用同步阻塞的请求处理模式。在高并发场景下，存在响应延迟高、资源利用率低等问题。

**核心目标：**

- 将AI请求处理吞吐量提升 **10倍以上**
- 实现请求削峰填谷，支持突发流量
- 降低用户等待时间，提升用户体验
- 增强系统弹性和可扩展性

### 1.2 核心优势

| 优势         | 说明                               |
| :----------- | :--------------------------------- |
| **异步解耦** | 请求发送后立即返回，无需等待AI响应 |
| **削峰填谷** | 高并发时消息积压，平滑处理流量峰值 |
| **弹性扩展** | 增加消费者实例即可提升处理能力     |
| **高可用**   | 消息持久化，失败重试，故障恢复     |
| **可观测**   | 支持消息追踪、监控指标采集         |

### 1.3 技术选型理由

**选择Apache Kafka而非RabbitMQ的原因：**

| 特性       | Kafka            | RabbitMQ  | 选择Kafka的理由        |
| :--------- | :--------------- | :-------- | :--------------------- |
| 吞吐量     | 百万级/秒        | 十万级/秒 | AI请求量大，需要高吞吐 |
| 消息持久化 | 基于磁盘顺序写入 | 基于队列  | 更可靠的消息持久化     |
| 分区机制   | 原生支持         | 需要插件  | 便于水平扩展和负载均衡 |
| 消息回溯   | 支持             | 不支持    | 便于故障排查和重放     |
| 生态集成   | 完善             | 完善      | Spring生态支持良好     |

**注意：** 项目中RabbitMQ已配置但未使用，我们将在方案中说明如何处理这一遗留配置。

------

## 2. 现状分析

### 2.1 项目技术栈

| 层级       | 技术选型                   | 版本   |
| :--------- | :------------------------- | :----- |
| 应用框架   | Spring Boot                | 3.5.0  |
| AI框架     | LangChain4j                | 1.5.0  |
| AI框架     | Spring AI                  | 1.0.0  |
| 编程语言   | Java                       | 17     |
| 构建工具   | Maven                      | 3.x    |
| 向量数据库 | Qdrant                     | latest |
| 关系数据库 | MySQL                      | 8.0+   |
| 文档数据库 | MongoDB                    | latest |
| 缓存       | Redis                      | latest |
| 消息队列   | RabbitMQ（已配置，未使用） | latest |

### 2.2 AI模型支持

项目当前支持以下AI模型：

YAMLCopy

```
# 配置文件位置: src/main/resources/application-docker.yml

ai:
  deepSeek:
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat        # 默认使用
    apiKey: ${DeepSeek_API_KEY:}
  
  kimi:
    base-url: https://api.moonshot.cn/v1
    model: kimi-k2-turbo-preview
  
  dashscope:  # 阿里云通义千问
    apiKey: ${DASHSCOPE_API_KEY:}
    model: qwen-vl-max
  
  ollama:  # 本地模型
    base-url: http://localhost:11434
    model: deepseek-r1:8b
```

### 2.3 当前请求处理流程

Copy

```
┌─────────────────────────────────────────────────────────────────┐
│                         同步处理流程                              │
└─────────────────────────────────────────────────────────────────┘

用户请求 (POST /xiaozhi/chat)
       │
       ▼
┌─────────────────┐
│ ChatController  │  接收请求参数 (memoryId, message)
│   .chat()       │  异常捕获，返回友好错误信息
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ ChatServiceImpl │  调用 processByUserMeanings()
│   .chat()       │  保存聊天记录
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 意图识别阶段     │  调用 ChatTypeAssistant.chat()
│ ChatTypeAssistant│  LLM调用 #1：意图分类
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 业务路由         │  根据意图分发到对应Agent
│                 │  • medical → DoctorAgent
│                 │  • translation → TranslaterService
│                 │  • term_extraction → TermExtractionAgent
│                 │  • sql_operation → NL2SQLService
│                 │  • default → ChatAssistant
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 业务处理阶段     │  调用具体业务Agent
│                 │  LLM调用 #2：业务处理
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 返回结果         │  同步返回AI生成结果
└─────────────────┘

平均响应时间: 2-5秒（取决于AI模型响应速度）
```

### 2.4 核心瓶颈分析

#### 2.4.1 同步阻塞问题

**问题描述：**

JAVACopy

```
// ChatController.java:37-50
@PostMapping("/chat")
public String chat(@RequestBody ChatForm chatForm) {
    // 请求线程被阻塞，等待AI响应
    String result = chatService.chat(memoryId, userMessage);  // 阻塞点
    return result;
}
```

**影响分析：**

- 每个请求占用一个线程池线程
- AI响应时间2-5秒，线程长时间阻塞
- 线程池耗尽时新请求被拒绝

#### 2.4.2 多次LLM调用

**问题描述：** 每次请求需要调用LLM两次：

1. **意图识别调用**：`ChatTypeAssistant.chat()` - 消耗~500ms
2. **业务处理调用**：`DoctorAgent/TranslaterService/ChatAssistant` - 消耗1-4秒

**处理流程代码位置：**

JAVACopy

```
// ChatServiceImpl.java:61-157
private String processByUserMeanings(Long memoryId, String userMessage) {
    // 第一次LLM调用：意图识别
    String aiResponse = chatTypeAssistant.chat(tempMemoryId, userMessage);
    
    // 第二次LLM调用：业务处理
    if (MEDICAL_TYPE.equals(intent)) {
        result = doctorAgent.chat(memoryId, userMessage);  // 第二次调用
    }
    // ... 其他业务处理
}
```

#### 2.4.3 无流量控制机制

**当前状态：**

- 无请求队列缓冲
- 无限流降级策略
- 突发流量直接冲击后端服务
- 可能导致AI API调用超限

#### 2.4.4 现有资源利用率

| 资源     | 当前使用情况           | 问题             |
| :------- | :--------------------- | :--------------- |
| 线程池   | 15核心线程，30最大线程 | 高并发时线程不足 |
| RabbitMQ | 已配置，未使用         | 资源浪费         |
| Redis    | 仅配置，未使用         | 缓存能力未利用   |
| 硬件资源 | 利用率低               | 无法应对流量峰值 |

### 2.5 现有基础设施状态

#### 2.5.1 RabbitMQ配置（已配置但未使用）

YAMLCopy

```
# application-docker.yml:44-49
spring:
  rabbitmq:
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:admin}
    password: ${SPRING_RABBITMQ_PASSWORD:admin}
```

**pom.xml依赖状态：**

XMLCopy

```
<!-- Spring Boot AMQP 自动配置，无需额外依赖 -->
<!-- 配置已就绪，但代码中无生产者/消费者实现 -->
```

#### 2.5.2 线程池配置

JAVACopy

```
// ThreadPoolConfig.java:15-26
@Bean(name = "mdcExecutorService")
public ThreadPoolExecutor mdcExecutorService() {
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100);
    return new MDCThreadPoolExecutor(
        15,                    // 核心线程数
        30,                    // 最大线程数
        60L, TimeUnit.SECONDS,
        workQueue,
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
```

**业务Constant定义：**

JAVACopy

```
// BusinessConstant.java
public static final int THREAD_POOL_SIZE = 15;
public static final int THREAD_POOL_QUEUE_SIZE = 100;
```

------

## 3. 架构设计

### 3.1 整体架构图

Copy

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                    用户层                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  Web前端      │    │  移动APP     │    │  第三方服务   │    │  API调用方    │      │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘    └──────┬───────┘      │
└─────────┼───────────────────┼───────────────────┼───────────────────┼──────────────┘
          │                   │                   │                   │
          ▼                   ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                 API网关层                                             │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │  限流、认证、日志、路由                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              同步接口层（保留原有接口）                                 │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │ /xiaozhi/chat  │  │ /xiaozhi/chat2 │  │ /ragTranslation/│  │ /ragTranslation/│   │
│  │ (同步保留)      │  │ (同步保留)      │  │  chat          │  │  chatStream    │    │
│  └────────────────┘  └────────────────┘  └────────────────┘  └────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              异步接口层（新增Kafka接口）                               │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐    │
│  │ /xiaozhi/chat/ │  │ /xiaozhi/result│  │ /xiaozhi/chat/ │  │ /xiaozhi/chat/ │    │
│  │ async          │  │ /{requestId}   │  │ stream/{id}    │  │ batch          │    │
│  │ (异步提交)      │  │ (轮询结果)      │  │ (SSE推送)      │  │ (批量处理)      │    │
│  └────────────────┘  └────────────────┘  └────────────────┘  └────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              Kafka消息处理层                                          │
│                                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │                              Kafka Cluster                                   │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌──────────┐  │    │
│  │  │ ai-chat-request│  │ ai-intent      │  │ ai-process     │  │ai-result │  │    │
│  │  │ (请求队列)      │  │ (意图队列)      │  │ (处理队列)      │  │(结果队列) │  │    │
│  │  │ Partition: 6   │  │ Partition: 3   │  │ Partition: 6   │  │Part: 6   │  │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘  └──────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
          │
          ┌───────────────────────┬───────────────────────┐
          ▼                       ▼                       ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│   意图识别消费者   │ │   业务处理消费者   │ │   结果处理消费者   │
│ IntentConsumer    │ │ ProcessConsumer   │ │ ResultConsumer    │
│ 并发数: 3         │ │ 并发数: 5         │ │ 并发数: 3         │
│                   │ │                   │ │                   │
│ • 解析用户意图     │ │ • 医疗Agent       │ │ • 聚合处理结果     │
│ • 路由到对应队列   │ │ • 翻译Service     │ │ • 缓存结果         │
│ • 处理时间: ~500ms│ │ • 术语提取Agent   │ │ • SSE推送通知      │
│                   │ │ • SQL生成Service  │ │ • WebSocket推送   │
│                   │ │ • 默认ChatAssistant│ │                   │
└───────┬───────────┘ └───────┬───────────┘ └───────┬───────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐
│   路由到           │ │   AI模型调用       │ │   存储到           │
│   ai-process      │ │                   │ │                   │
│   主题            │ │ • DeepSeek        │ │ • Redis (缓存)     │
│                   │ │ • Kimi            │ │ • MongoDB (历史)   │
│                   │ │ • Ollama          │ │ • MySQL (日志)     │
└───────────────────┘ └───────────────────┘ └───────────────────┘
```

### 3.2 数据流转设计

#### 3.2.1 消息流转图

Copy

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                 消息生命周期                                          │
└─────────────────────────────────────────────────────────────────────────────────────┘

  用户请求                          Kafka处理                              结果获取
     │                                 │                                      │
     │  POST /chat/async               │                                      │
     ├────────────────────────────────>                                      │
     │  {memoryId, message}            │                                      │
     │                                 │                                      │
     │  返回 {requestId, status}       │                                      │
     <────────────────────────────────                                      │
     │                                 │                                      │
     │                                 │ 消费 ai-chat-request 主题              │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 意图识别                              │
     │                                 │ ChatTypeAssistant.chat()              │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 路由到 ai-process 主题                 │
     │                                 ├─────────────────────────────────────>
     │                                 │ (带意图标签)                          │
     │                                 │                                      │
     │                                 │ 消费 ai-process 主题                  │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 业务处理                              │
     │                                 │ DoctorAgent/TranslaterService         │
     │                                 │ /ChatAssistant.chat()                 │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 发送结果到 ai-result 主题              │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 消费 ai-result 主题                   │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │                                 │ 缓存到 Redis                          │
     │                                 ├─────────────────────────────────────>
     │                                 │                                      │
     │  GET /result/{requestId}        │                                      │
     ├────────────────────────────────────────────────────────────────────────────>
     │                                 │                                      │
     │  返回处理结果                    │                                      │
     <────────────────────────────────────────────────────────────────────────────
     │                                 │                                      │

  生命周期: ~2-5秒 (纯处理时间)
  用户感知: 立即返回 (异步模式)
```

#### 3.2.2 消息格式设计

**请求消息格式：**

JSONCopy

```
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "memoryId": 1234567890,
  "message": "请帮我翻译这段医学文献",
  "userId": "user_001",
  "timestamp": "2026-01-15T10:30:00",
  "callbackUrl": null,
  "messageType": "TRANSLATION",
  "metadata": {
    "source": "web",
    "priority": "normal",
    "retryCount": 0
  }
}
```

**结果消息格式：**

JSONCopy

```
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "memoryId": 1234567890,
  "result": "Here is the translation...",
  "status": "SUCCESS",
  "errorMessage": null,
  "processedAt": "2026-01-15T10:30:02.500",
  "processingTimeMs": 2500,
  "intent": "translation",
  "tokenUsage": {
    "promptTokens": 150,
    "completionTokens": 200,
    "totalTokens": 350
  }
}
```

### 3.3 Topic设计

#### 3.3.1 Topic列表

| Topic名称         | 分区数 | 副本数 | 用途         | 消息保留 |
| :---------------- | :----- | :----- | :----------- | :------- |
| `ai-chat-request` | 6      | 1      | 用户请求入口 | 7天      |
| `ai-intent`       | 3      | 1      | 意图识别结果 | 1天      |
| `ai-process`      | 6      | 1      | 业务处理队列 | 7天      |
| `ai-result`       | 6      | 1      | 处理结果输出 | 1天      |
| `ai-dlq`          | 1      | 1      | 死信队列     | 30天     |

#### 3.3.2 分区策略

JAVACopy

```
// 请求消息使用 requestId 作为分区键，确保同一请求的消息有序
public int partition(String topic, String key, byte[] keyBytes, 
                     Object value, Cluster cluster) {
    // 使用 key 的哈希值确定分区
    if (key == null) {
        return random.nextInt(6);  // 随机分区
    }
    return Math.abs(key.hashCode() % 6);
}
```

**分区分配策略：**

- **Partition 0-1**: 高优先级请求（VIP用户、付费请求）
- **Partition 2-3**: 普通优先级请求
- **Partition 4-5**: 低优先级请求（批量处理、后台任务）

### 3.4 消费者组设计

#### 3.4.1 消费者组列表

| 消费者组                 | 消费的Topic     | 并发数 | 职责                  |
| :----------------------- | :-------------- | :----- | :-------------------- |
| `ai-intent-consumer`     | ai-chat-request | 3      | 意图识别              |
| `ai-process-medical`     | ai-process      | 2      | 医疗Agent处理         |
| `ai-process-translation` | ai-process      | 2      | 翻译Service处理       |
| `ai-process-general`     | ai-process      | 3      | 默认ChatAssistant处理 |
| `ai-result-consumer`     | ai-result       | 3      | 结果聚合与缓存        |
| `ai-dlq-consumer`        | ai-dlq          | 1      | 死信处理              |

#### 3.4.2 负载均衡策略

Copy

```
                    Kafka集群
                        │
        ┌───────────────┼───────────────┐
        │               │               │
   Partition 0     Partition 1    Partition 2
        │               │               │
   ┌────┴────┐    ┌────┴────┐    ┌────┴────┐
   │Consumer │    │Consumer │    │Consumer │
   │Instance1│    │Instance2│    │Instance3│
   │Group: ai│    │Group: ai│    │Group: ai│
   └─────────┘    └─────────┘    └─────────┘

   消费模型: 同一消费者组内的消费者平均分配分区
   扩展模式: 增加消费者实例自动重新分配分区
```

### 3.5 核心设计原则

#### 3.5.1 请求-响应分离

**设计模式：** Fire-and-Forget + Polling

JAVACopy

```
// 控制器层
@PostMapping("/chat/async")
public Map<String, Object> asyncChat(@RequestBody ChatForm chatForm) {
    // 1. 创建请求消息
    ChatRequestMessage request = ChatRequestMessage.create(
        chatForm.getMemoryId(), 
        chatForm.getMessage()
    );
    
    // 2. 异步发送到Kafka（不等待处理完成）
    requestProducer.sendRequest(request);
    
    // 3. 立即返回任务ID
    return Map.of(
        "requestId", request.getRequestId(),
        "status", "PROCESSING",
        "resultUrl", "/xiaozhi/result/" + request.getRequestId()
    );
}
```

#### 3.5.2 流水线处理

Copy

```
阶段1: 请求接收        阶段2: 意图识别        阶段3: 业务处理        阶段4: 结果返回
     │                    │                    │                    │
     ▼                    ▼                    ▼                    ▼
┌─────────┐          ┌─────────┐          ┌─────────┐          ┌─────────┐
│  接收   │─────────>│  意图   │─────────>│  业务   │─────────>│  结果   │
│  请求   │          │  识别   │          │  处理   │          │  缓存   │
└─────────┘          └─────────┘          └─────────┘          └─────────┘
     │                    │                    │                    │
   ~50ms              ~500ms              ~2000ms               ~50ms
```

**优势：**

- 各阶段可独立扩展
- 失败不影响其他阶段
- 便于监控和优化

#### 3.5.3 结果持久化

JAVACopy

```
// 消费者处理完成后，将结果写入Redis
@KafkaListener(topics = "ai-result")
public void handleResult(ChatResultMessage result) {
    // 1. 序列化结果
    String json = objectMapper.writeValueAsString(result);
    
    // 2. 写入Redis，设置24小时过期
    stringRedisTemplate.opsForValue()
        .set("chat:result:" + result.getRequestId(), json, Duration.ofHours(24));
    
    // 3. 可选：写入MongoDB历史记录
    mongoChatMemoryStore.saveChatResult(result);
}
```

------

## 4. 实现方案

### 4.1 依赖配置

#### 4.1.1 Maven依赖（pom.xml）

XMLCopy

```
<!-- 在 <dependencies> 标签内添加以下依赖 -->

<!-- ==================== Kafka 依赖 ==================== -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- ==================== Jackson JSON处理 ==================== -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
</dependency>

<!-- ==================== Lombok（如果项目未使用） ==================== -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**注意：** 如果项目当前未使用Lombok，需要先添加Lombok支持或在Bean中手动添加getter/setter。

#### 4.1.2 版本管理

在 `<dependencyManagement>` 的 `<dependencies>` 中添加：

XMLCopy

```
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
    <version>3.1.0</version>
</dependency>
```

### 4.2 配置文件

#### 4.2.1 application-docker.yml 新增配置

YAMLCopy

```
# ==================== Kafka配置 ====================
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    
    # 生产者配置
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all                                    # 所有分区确认
      retries: 3                                   # 重试次数
      retry-backoff-ms: 100                        # 重试间隔
      enable-idempotence: true                     # 幂等性保证
      max-in-flight-requests-per-connection: 5     # 最大飞行中请求数
      buffer-memory: 33554432                      # 32MB缓冲区
      compression-type: lz4                        # LZ4压缩
      linger-ms: 5                                 # 批量发送延迟
      
    # 消费者配置
    consumer:
      group-id: ai-chat-consumer-group
      auto-offset-reset: earliest                  # 从最早位置消费
      enable-auto-commit: false                    # 手动提交offset
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      max-poll-records: 100                        # 每次拉取最大消息数
      fetch-min-size: 102400                       # 最小100KB
      fetch-max-wait-ms: 500                       # 最多等待500ms
      properties:
        spring.json.trusted.packages: "*"          # JSON反序列化信任包
        spring.json.value.default.type: org.fb.bean.kafka.ChatRequestMessage
        
    # 监听器配置
    listener:
      ack-mode: manual                             # 手动确认
      concurrency: 5                               # 并发消费者数
      type: batch                                  # 批量消费
      missing-topics-fatal: false                  # Topic不存在不报错

# ==================== 自定义Kafka配置 ====================
kafka:
  topics:
    request: ai-chat-request
    intent: ai-intent
    process: ai-process
    result: ai-chat-result
    dlq: ai-dlq
    
  consumer-groups:
    request: ai-request-consumer
    intent: ai-intent-consumer
    process: ai-process-consumer
    result: ai-result-consumer
    dlq: ai-dlq-consumer
    
  # 消息保留时间（毫秒）
  retention:
    request: 604800000      # 7天
    intent: 86400000        # 1天
    process: 604800000      # 7天
    result: 86400000        # 1天
    
  # 重试配置
  retry:
    max-attempts: 3
    initial-interval: 1000
    multiplier: 2.0
    max-interval: 10000
```

#### 4.2.2 多环境配置文件

**application-standalone.yml:**

YAMLCopy

```
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

**application-ubuntu.yml:**

YAMLCopy

```
spring:
  kafka:
    bootstrap-servers: ${KAFKA_HOST:192.168.1.100}:9092
```

### 4.3 核心组件实现

#### 4.3.1 消息实体类

**ChatRequestMessage.java**

JAVACopy

```
package org.fb.bean.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AI聊天请求消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 请求唯一标识
     */
    @JsonProperty("request_id")
    private String requestId;
    
    /**
     * 对话记忆ID
     */
    @JsonProperty("memory_id")
    private Long memoryId;
    
    /**
     * 用户消息内容
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * 用户ID（可选）
     */
    @JsonProperty("user_id")
    private String userId;
    
    /**
     * 请求时间戳
     */
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 回调URL（可选，用于webhook通知）
     */
    @JsonProperty("callback_url")
    private String callbackUrl;
    
    /**
     * 消息类型
     */
    @JsonProperty("message_type")
    private MessageType messageType;
    
    /**
     * 请求元数据
     */
    @JsonProperty("metadata")
    private RequestMetadata metadata;
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        CHAT,           // 普通对话
        TRANSLATION,    // 翻译请求
        MEDICAL,        // 医疗咨询
        TERM_EXTRACT,   // 术语提取
        SQL_QUERY,      // SQL查询
        UNKNOWN         // 未知类型
    }
    
    /**
     * 请求元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestMetadata implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("source")
        private String source;         // 请求来源 (web/mobile/api)
        
        @JsonProperty("priority")
        private Priority priority;     // 优先级
        
        @JsonProperty("retry_count")
        private int retryCount;        // 重试次数
        
        @JsonProperty("client_info")
        private ClientInfo clientInfo; // 客户端信息
    }
    
    /**
     * 优先级枚举
     */
    public enum Priority {
        LOW(0),
        NORMAL(1),
        HIGH(2),
        VIP(3);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * 客户端信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("ip")
        private String ip;
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        @JsonProperty("platform")
        private String platform;
    }
    
    /**
     * 创建请求消息的工厂方法
     */
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
     * 创建带优先级的请求消息
     */
    public static ChatRequestMessage create(Long memoryId, String message, 
                                            MessageType type, Priority priority) {
        return ChatRequestMessage.builder()
                .requestId(UUID.randomUUID().toString())
                .memoryId(memoryId)
                .message(message)
                .timestamp(LocalDateTime.now())
                .messageType(type)
                .metadata(RequestMetadata.builder()
                        .source("api")
                        .priority(priority)
                        .retryCount(0)
                        .build())
                .build();
    }
}
```

**ChatResultMessage.java**

JAVACopy

```
package org.fb.bean.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI聊天结果消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResultMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 请求ID（与请求消息对应）
     */
    @JsonProperty("request_id")
    private String requestId;
    
    /**
     * 对话记忆ID
     */
    @JsonProperty("memory_id")
    private Long memoryId;
    
    /**
     * 处理结果
     */
    @JsonProperty("result")
    private String result;
    
    /**
     * 处理状态
     */
    @JsonProperty("status")
    private ResultStatus status;
    
    /**
     * 错误信息（失败时）
     */
    @JsonProperty("error_message")
    private String errorMessage;
    
    /**
     * 处理完成时间
     */
    @JsonProperty("processed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime processedAt;
    
    /**
     * 处理耗时（毫秒）
     */
    @JsonProperty("processing_time_ms")
    private long processingTimeMs;
    
    /**
     * 识别的意图类型
     */
    @JsonProperty("intent")
    private String intent;
    
    /**
     * Token使用统计
     */
    @JsonProperty("token_usage")
    private TokenUsage tokenUsage;
    
    /**
     * 结果状态枚举
     */
    public enum ResultStatus {
        SUCCESS,     // 成功
        PROCESSING,  // 处理中
        FAILED,      // 失败
        TIMEOUT,     // 超时
        CANCELLED    // 取消
    }
    
    /**
     * Token使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        
        @JsonProperty("completion_tokens")
        private int completionTokens;
        
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
    
    /**
     * 创建成功结果
     */
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
    
    /**
     * 创建处理中状态
     */
    public static ChatResultMessage processing(String requestId, Long memoryId) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ResultStatus.PROCESSING)
                .processedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static ChatResultMessage failed(String requestId, Long memoryId, String errorMessage) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ResultStatus.FAILED)
                .errorMessage(errorMessage)
                .processedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建超时结果
     */
    public static ChatResultMessage timeout(String requestId, Long memoryId) {
        return ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ResultStatus.TIMEOUT)
                .errorMessage("Processing timeout")
                .processedAt(LocalDateTime.now())
                .build();
    }
}
```

#### 4.3.2 Kafka配置类

**KafkaConfig.java**

JAVACopy

```
package org.fb.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Kafka配置类
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    // ==================== Topic定义 ====================
    
    /**
     * AI请求Topic
     */
    @Bean
    public NewTopic aiRequestTopic() {
        return TopicBuilder.name("ai-chat-request")
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
                .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "10485760")  // 10MB
                .build();
    }
    
    /**
     * 意图识别Topic
     */
    @Bean
    public NewTopic aiIntentTopic() {
        return TopicBuilder.name("ai-intent")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }
    
    /**
     * 业务处理Topic
     */
    @Bean
    public NewTopic aiProcessTopic() {
        return TopicBuilder.name("ai-process")
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "10485760")
                .build();
    }
    
    /**
     * 结果Topic
     */
    @Bean
    public NewTopic aiResultTopic() {
        return TopicBuilder.name("ai-chat-result")
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }
    
    /**
     * 死信队列Topic
     */
    @Bean
    public NewTopic aiDlqTopic() {
        return TopicBuilder.name("ai-dlq")
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000")  // 30天
                .build();
    }
    
    // ==================== Producer Factory ====================
    
    /**
     * 请求消息生产者工厂
     */
    @Bean
    public ProducerFactory<String, ChatRequestMessage> chatRequestProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432L);  // 32MB
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * 请求消息KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, ChatRequestMessage> chatRequestKafkaTemplate() {
        return new KafkaTemplate<>(chatRequestProducerFactory());
    }
    
    /**
     * 结果消息生产者工厂
     */
    @Bean
    public ProducerFactory<String, ChatResultMessage> chatResultProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * 结果消息KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, ChatResultMessage> chatResultKafkaTemplate() {
        return new KafkaTemplate<>(chatResultProducerFactory());
    }
    
    // ==================== Consumer Factory ====================
    
    /**
     * 请求消息消费者工厂
     */
    @Bean
    public ConsumerFactory<String, ChatRequestMessage> chatRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-request-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 102400);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        // JSON反序列化配置
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatRequestMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * 请求消息监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> 
            chatRequestListenerContainerFactory() {
        
        ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(chatRequestConsumerFactory());
        factory.setConcurrency(5);  // 并发消费者数
        factory.setBatchListener(false);  // 单条消费
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // 错误处理配置
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, exception) -> {
                // 记录到死信队列
            },
            new FixedBackOff(1000L, 3L)  // 重试3次，间隔1秒
        );
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
    
    /**
     * 结果消息消费者工厂
     */
    @Bean
    public ConsumerFactory<String, ChatResultMessage> chatResultConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-result-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatResultMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * 结果消息监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatResultMessage> 
            chatResultListenerContainerFactory() {
        
        ConcurrentKafkaListenerContainerFactory<String, ChatResultMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(chatResultConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }
    
    // ==================== Admin配置 ====================
    
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }
}
```

#### 4.3.3 Kafka生产者服务

**ChatRequestProducer.java**

JAVACopy

```
package org.fb.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka消息生产者服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRequestProducer {

    private final KafkaTemplate<String, ChatRequestMessage> requestKafkaTemplate;
    private final KafkaTemplate<String, ChatResultMessage> resultKafkaTemplate;
    
    private static final String AI_REQUEST_TOPIC = "ai-chat-request";
    private static final String AI_RESULT_TOPIC = "ai-chat-result";
    private static final String AI_DLQ_TOPIC = "ai-dlq";
    
    /**
     * 发送AI请求到Kafka
     * 
     * @param request 请求消息
     * @return CompletableFuture 异步发送结果
     */
    public CompletableFuture<SendResult<String, ChatRequestMessage>> sendRequest(
            ChatRequestMessage request) {
        
        log.info("发送AI请求到Kafka, requestId: {}, memoryId: {}, message: {}", 
                request.getRequestId(), 
                request.getMemoryId(), 
                request.getMessage());
        
        // 使用requestId作为key，确保消息有序
        return requestKafkaTemplate.send(AI_REQUEST_TOPIC, request.getRequestId(), request)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("请求发送成功, requestId: {}, partition: {}, offset: {}", 
                                request.getRequestId(), 
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("请求发送失败, requestId: {}", request.getRequestId(), ex);
                        // 发送到死信队列
                        sendToDlq(request, ex.getMessage());
                    }
                });
    }
    
    /**
     * 发送处理结果到Kafka
     * 
     * @param result 结果消息
     * @return CompletableFuture 异步发送结果
     */
    public CompletableFuture<SendResult<String, ChatResultMessage>> sendResult(
            ChatResultMessage result) {
        
        log.info("发送处理结果到Kafka, requestId: {}, status: {}", 
                result.getRequestId(), 
                result.getStatus());
        
        return resultKafkaTemplate.send(AI_RESULT_TOPIC, result.getRequestId(), result)
                .whenComplete((sendResult, ex) -> {
                    if (ex == null) {
                        log.info("结果发送成功, requestId: {}", result.getRequestId());
                    } else {
                        log.error("结果发送失败, requestId: {}", result.getRequestId(), ex);
                    }
                });
    }
    
    /**
     * 发送处理中状态
     */
    public void sendProcessingStatus(String requestId, Long memoryId) {
        ChatResultMessage processingResult = ChatResultMessage.builder()
                .requestId(requestId)
                .memoryId(memoryId)
                .status(ChatResultMessage.ResultStatus.PROCESSING)
                .processedAt(java.time.LocalDateTime.now())
                .build();
        
        sendResult(processingResult);
    }
    
    /**
     * 发送失败状态
     */
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
    
    /**
     * 发送到死信队列
     */
    private void sendToDlq(ChatRequestMessage request, String errorMessage) {
        try {
            log.warn("发送请求到死信队列, requestId: {}, error: {}", 
                    request.getRequestId(), errorMessage);
            
            // 可以在死信消息中添加错误信息
            // 这里简单转发原始消息，实际可根据需要修改
            requestKafkaTemplate.send(AI_DLQ_TOPIC, request.getRequestId(), request);
        } catch (Exception e) {
            log.error("发送死信队列失败, requestId: {}", request.getRequestId(), e);
        }
    }
    
    /**
     * 批量发送请求（用于批量处理场景）
     * 
     * @param requests 请求消息列表
     * @return CompletableFuture数组
     */
    public CompletableFuture<SendResult<String, ChatRequestMessage>>[] sendBatch(
            java.util.List<ChatRequestMessage> requests) {
        
        log.info("批量发送AI请求, 数量: {}", requests.size());
        
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, ChatRequestMessage>>[] futures = 
                new CompletableFuture[requests.size()];
        
        for (int i = 0; i < requests.size(); i++) {
            futures[i] = sendRequest(requests.get(i));
        }
        
        return futures;
    }
}
```

#### 4.3.4 Kafka消费者服务

**ChatRequestConsumer.java**

JAVACopy

```
package org.fb.service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.constant.BusinessConstant;
import org.fb.service.assistant.*;
import org.fb.service.ChatService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kafka消息消费者服务
 */
@Slf4j
@Service
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
    
    /**
     * 消费AI请求消息
     * 
     * @param record Kafka消息记录
     * @param ack 手动确认
     */
    @KafkaListener(
            topics = "ai-chat-request",
            groupId = "ai-request-consumer",
            containerFactory = "chatRequestListenerContainerFactory"
    )
    public void consumeChatRequest(
            ConsumerRecord<String, ChatRequestMessage> record,
            Acknowledgment ack) {
        
        ChatRequestMessage request = record.value();
        log.info("收到AI请求, requestId: {}, partition: {}, offset: {}", 
                request.getRequestId(), 
                record.partition(), 
                record.offset());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 异步处理请求，不阻塞Kafka消费者
            CompletableFuture.supplyAsync(() -> processRequest(request), mdcExecutorService)
                    .thenAccept(result -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        result.setProcessingTimeMs(processingTime);
                        
                        // 缓存结果
                        cacheResult(request.getRequestId(), result);
                        
                        // 发送结果到结果主题
                        requestProducer.sendResult(result);
                        
                        log.info("请求处理完成, requestId: {}, 处理时间: {}ms, 状态: {}", 
                                request.getRequestId(), 
                                processingTime,
                                result.getStatus());
                        
                        // 手动确认消息
                        ack.acknowledge();
                    })
                    .exceptionally(ex -> {
                        log.error("请求处理异常, requestId: {}", request.getRequestId(), ex);
                        
                        // 发送失败状态
                        requestProducer.sendFailedStatus(
                                request.getRequestId(), 
                                request.getMemoryId(), 
                                ex.getMessage()
                        );
                        
                        // 确认消息，避免重复消费
                        ack.acknowledge();
                        return null;
                    });
                    
        } catch (Exception e) {
            log.error("消费消息异常, requestId: {}", request.getRequestId(), e);
            
            // 发送失败状态
            requestProducer.sendFailedStatus(request.getRequestId(), request.getMemoryId(), e.getMessage());
            
            // 确认消息
            ack.acknowledge();
        }
    }
    
    /**
     * 消费处理结果消息（用于结果聚合、通知等）
     */
    @KafkaListener(
            topics = "ai-chat-result",
            groupId = "ai-result-consumer",
            containerFactory = "chatResultListenerContainerFactory"
    )
    public void consumeChatResult(
            ConsumerRecord<String, ChatResultMessage> record,
            Acknowledgment ack) {
        
        ChatResultMessage result = record.value();
        log.info("收到处理结果, requestId: {}, status: {}", 
                result.getRequestId(), 
                result.getStatus());
        
        try {
            // 如果有回调URL，触发回调
            if (result.getStatus() == ChatResultMessage.ResultStatus.SUCCESS) {
                triggerCallback(result);
            }
            
            // 确认消息
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("处理结果消息异常, requestId: {}", result.getRequestId(), e);
            ack.acknowledge();
        }
    }
    
    /**
     * 处理AI请求核心逻辑
     */
    private ChatResultMessage processRequest(ChatRequestMessage request) {
        String result;
        String intent;
        
        try {
            // 阶段1: 意图识别
            log.info("开始意图识别, requestId: {}", request.getRequestId());
            intent = recognizeIntent(request.getMemoryId(), request.getMessage());
            log.info("意图识别完成, requestId: {}, intent: {}", 
                    request.getRequestId(), intent);
            
            // 阶段2: 根据意图路由到对应的业务处理器
            log.info("开始业务处理, requestId: {}, intent: {}", 
                    request.getRequestId(), intent);
            
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
                    result = nl2SQLService.executeNaturalLanguageQuery(request.getMessage())
                            .toString();
                    break;
                default:
                    result = chatAssistant.chat(request.getMemoryId(), request.getMessage());
            }
            
            log.info("业务处理完成, requestId: {}, resultLength: {}", 
                    request.getRequestId(), 
                    result != null ? result.length() : 0);
            
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
    
    /**
     * 意图识别（复用现有逻辑）
     */
    private String recognizeIntent(Long memoryId, String message) {
        Long tempMemoryId = System.currentTimeMillis();
        String aiResponse = chatTypeAssistant.chat(tempMemoryId, message);
        return extractIntent(aiResponse);
    }
    
    /**
     * 提取意图（从ChatServiceImpl复制）
     */
    private String extractIntent(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return BusinessConstant.DEFAULT_TYPE;
        }
        
        // 尝试解析JSON格式的响应
        try {
            Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String intent = matcher.group(1).trim().toLowerCase();
                log.info("从JSON中提取的intent: {}", intent);
                return intent;
            }
        } catch (Exception e) {
            log.warn("解析JSON intent失败，返回原始响应", e);
        }
        
        // 如果JSON解析失败，使用旧的方式进行兼容（兜底策略）
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
    
    /**
     * 缓存处理结果
     */
    private void cacheResult(String requestId, ChatResultMessage result) {
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(cacheKey, jsonResult, RESULT_TTL);
            log.info("结果已缓存, requestId: {}, cacheKey: {}", requestId, cacheKey);
        } catch (Exception e) {
            log.error("缓存结果失败, requestId: {}", requestId, e);
        }
    }
    
    /**
     * 触发回调通知
     */
    private void triggerCallback(ChatResultMessage result) {
        // 如果有回调URL，可以在这里发起HTTP回调
        // 使用WebClient或RestTemplate
        log.info("触发回调通知, requestId: {}", result.getRequestId());
        
        // 示例实现：
        // webClient.post()
        //     .uri(result.getCallbackUrl())
        //     .bodyValue(result)
        //     .retrieve()
        //     .toBodilessEntity()
        //     .subscribe();
    }
}
```

#### 4.3.5 异步控制器实现

**AsyncChatController.java**

JAVACopy

```
package org.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.ChatForm;
import org.fb.bean.kafka.ChatRequestMessage;
import org.fb.bean.kafka.ChatResultMessage;
import org.fb.service.kafka.ChatRequestProducer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 异步聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/xiaozhi")
@RequiredArgsConstructor
public class AsyncChatController {

    private final ChatRequestProducer requestProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String RESULT_CACHE_PREFIX = "chat:result:";
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final long SSE_TIMEOUT = 300000L;  // 5分钟
    
    /**
     * 异步聊天接口 - 立即返回任务ID
     * 
     * @param chatForm 聊天请求表单
     * @return 任务ID和状态
     */
    @PostMapping("/chat/async")
    public Map<String, Object> asyncChat(@RequestBody ChatForm chatForm) {
        Long memoryId = chatForm.getMemoryId();
        String userMessage = chatForm.getMessage();
        
        log.info("收到异步聊天请求, memoryId: {}, message: {}", memoryId, userMessage);
        
        // 创建请求消息
        ChatRequestMessage request = ChatRequestMessage.create(memoryId, userMessage);
        
        // 异步发送到Kafka（不等待处理完成）
        CompletableFuture<Void> sendFuture = requestProducer.sendRequest(request)
                .thenAccept(result -> log.info("请求已发送, requestId: {}", request.getRequestId()));
        
        // 立即返回任务ID
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", request.getRequestId());
        response.put("status", "PROCESSING");
        response.put("message", "请求已提交，请使用requestId查询结果");
        response.put("resultUrl", "/xiaozhi/result/" + request.getRequestId());
        response.put("streamUrl", "/xiaozhi/chat/stream/" + request.getRequestId());
        
        return response;
    }
    
    /**
     * 查询处理结果 - 轮询接口
     * 
     * @param requestId 请求ID
     * @return 处理结果
     */
    @GetMapping("/result/{requestId}")
    public Map<String, Object> getResult(@PathVariable String requestId) {
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        String resultJson = redisTemplate.opsForValue().get(cacheKey);
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        
        if (resultJson != null) {
            try {
                // 反序列化结果
                ChatResultMessage result = objectMapper.readValue(resultJson, ChatResultMessage.class);
                
                response.put("status", result.getStatus());
                response.put("result", result.getResult());
                response.put("processingTimeMs", result.getProcessingTimeMs());
                response.put("processedAt", result.getProcessedAt());
                
                if (result.getErrorMessage() != null) {
                    response.put("error", result.getErrorMessage());
                }
                
                // 成功后可选删除缓存
                // if (result.getStatus() == ChatResultMessage.ResultStatus.SUCCESS) {
                //     redisTemplate.delete(cacheKey);
                // }
                
            } catch (Exception e) {
                log.error("解析结果失败, requestId: {}", requestId, e);
                response.put("status", "ERROR");
                response.put("error", "解析结果失败");
            }
        } else {
            response.put("status", "PROCESSING");
            response.put("message", "请求正在处理中，请稍后重试");
            response.put("retryAfter", 2000);  // 建议2秒后重试
        }
        
        return response;
    }
    
    /**
     * SSE流式结果推送
     * 
     * @param requestId 请求ID
     * @return SSEEmitter
     */
    @GetMapping(value = "/chat/stream/{requestId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResult(@PathVariable String requestId) {
        log.info("建立SSE连接, requestId: {}", requestId);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        
        // 启动异步任务监听结果
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long timeout = SSE_TIMEOUT - 10000;  // 预留10秒超时
                
                // 发送连接建立事件
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data("{\"status\":\"connected\",\"requestId\":\"" + requestId + "\"}"));
                
                while (System.currentTimeMillis() - startTime < timeout) {
                    String resultJson = redisTemplate.opsForValue().get(cacheKey);
                    
                    if (resultJson != null) {
                        // 解析结果
                        ChatResultMessage result = objectMapper.readValue(
                                resultJson, ChatResultMessage.class);
                        
                        // 发送处理中状态（如果还在处理）
                        if (result.getStatus() == ChatResultMessage.ResultStatus.PROCESSING) {
                            emitter.send(SseEmitter.event()
                                    .name("processing")
                                    .data("{\"status\":\"processing\"}"));
                        } else {
                            // 发送最终结果
                            emitter.send(SseEmitter.event()
                                    .name("result")
                                    .data(resultJson));
                            
                            // 发送完成信号
                            emitter.complete();
                            log.info("SSE推送完成, requestId: {}", requestId);
                            return;
                        }
                    }
                    
                    // 等待一段时间再检查
                    Thread.sleep(500);
                }
                
                // 超时
                emitter.send(SseEmitter.event()
                        .name("timeout")
                        .data("{\"status\":\"timeout\",\"message\":\"处理超时\"}"));
                emitter.complete();
                
            } catch (Exception e) {
                log.error("SSE流异常, requestId: {}", requestId, e);
                emitter.completeWithError(e);
            }
        });
        
        // 超时处理
        emitter.onTimeout(() -> {
            log.warn("SSE超时, requestId: {}", requestId);
            try {
                emitter.send(SseEmitter.event()
                        .name("timeout")
                        .data("{\"status\":\"timeout\"}"));
            } catch (Exception e) {
                log.error("发送超时事件失败, requestId: {}", requestId, e);
            }
            emitter.complete();
        });
        
        // 完成处理
        emitter.onCompletion(() -> log.info("SSE连接完成, requestId: {}", requestId));
        emitter.onError(e -> log.error("SSE连接错误, requestId: {}", requestId, e));
        
        return emitter;
    }
    
    /**
     * 批量异步提交请求
     * 
     * @param requests 请求列表
     * @return 任务ID列表
     */
    @PostMapping("/chat/batch")
    public Map<String, Object> batchAsyncChat(@RequestBody java.util.List<ChatForm> requests) {
        log.info("收到批量异步请求, 数量: {}", requests.size());
        
        java.util.List<Map<String, String>> results = new java.util.ArrayList<>();
        
        for (ChatForm chatForm : requests) {
            ChatRequestMessage request = ChatRequestMessage.create(
                    chatForm.getMemoryId(), 
                    chatForm.getMessage());
            
            requestProducer.sendRequest(request);
            
            Map<String, String> item = new HashMap<>();
            item.put("requestId", request.getRequestId());
            item.put("status", "PROCESSING");
            results.add(item);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", requests.size());
        response.put("results", results);
        response.put("message", "批量请求已提交");
        
        return response;
    }
    
    /**
     * 取消正在处理的请求
     * 
     * @param requestId 请求ID
     * @return 取消结果
     */
    @DeleteMapping("/chat/{requestId}")
    public Map<String, Object> cancelRequest(@PathVariable String requestId) {
        log.info("取消请求, requestId: {}", requestId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", requestId);
        
        // 发送取消状态到结果队列
        ChatResultMessage cancelledResult = ChatResultMessage.builder()
                .requestId(requestId)
                .status(ChatResultMessage.ResultStatus.CANCELLED)
                .errorMessage("用户取消")
                .processedAt(LocalDateTime.now())
                .build();
        
        requestProducer.sendResult(cancelledResult);
        
        // 从Redis中删除缓存的结果（如果存在）
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        redisTemplate.delete(cacheKey);
        
        response.put("status", "CANCELLED");
        response.put("message", "请求已取消");
        
        return response;
    }
}
```

#### 4.3.6 结果查询服务

**ChatResultService.java**

JAVACopy

```
package org.fb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fb.bean.kafka.ChatResultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 聊天结果查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatResultService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String RESULT_CACHE_PREFIX = "chat:result:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    
    /**
     * 获取处理结果
     * 
     * @param requestId 请求ID
     * @return Optional包装的结果
     */
    public Optional<ChatResultMessage> getResult(String requestId) {
        String cacheKey = RESULT_CACHE_PREFIX + requestId;
        String resultJson = redisTemplate.opsForValue().get(cacheKey);
        
        if (resultJson == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(objectMapper.readValue(resultJson, ChatResultMessage.class));
        } catch (Exception e) {
            log.error("解析结果失败, requestId: {}", requestId, e);
            return Optional.empty();
        }
    }
    
    /**
     * 检查请求是否处理完成
     * 
     * @param requestId 请求ID
     * @return true表示已完成
     */
    public boolean isCompleted(String requestId) {
        return getResult(requestId)
                .map(result -> result.getStatus() != ChatResultMessage.ResultStatus.PROCESSING)
                .orElse(false);
    }
    
    /**
     * 等待请求完成（带超时）
     * 
     * @param requestId 请求ID
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 处理结果
     */
    public ChatResultMessage waitForResult(String requestId, long timeout, java.util.concurrent.TimeUnit unit) 
            throws java.util.concurrent.TimeoutException {
        
        long startTime = System.currentTimeMillis();
        long deadline = startTime + unit.toMillis(timeout);
        
        while (System.currentTimeMillis() < deadline) {
            Optional<ChatResultMessage> result = getResult(requestId);
            
            if (result.isPresent()) {
                ChatResultMessage message = result.get();
                if (message.getStatus() != ChatResultMessage.ResultStatus.PROCESSING) {
                    return message;
                }
            }
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待被中断", e);
            }
        }
        
        throw new java.util.concurrent.TimeoutException("等待结果超时");
    }
    
    /**
     * 获取结果（带默认错误处理）
     * 
     * @param requestId 请求ID
     * @param defaultResult 默认结果
     * @return 结果消息
     */
    public ChatResultMessage getResultOrDefault(String requestId, ChatResultMessage defaultResult) {
        return getResult(requestId).orElse(defaultResult);
    }
}
```

------

## 5. 性能优化

### 5.1 批处理机制

JAVACopy

```
/**
 * 批量消费配置
 */
@Bean
public ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> 
        batchChatRequestListenerContainerFactory() {
    
    ConcurrentKafkaListenerContainerFactory<String, ChatRequestMessage> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    
    factory.setConsumerFactory(chatRequestConsumerFactory());
    factory.setConcurrency(5);
    factory.setBatchListener(true);  // 启用批量消费
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    
    return factory;
}

/**
 * 批量消费处理器
 */
@KafkaListener(
        topics = "ai-chat-request",
        groupId = "ai-batch-consumer",
        containerFactory = "batchChatRequestListenerContainerFactory"
)
public void consumeBatch(List<ChatRequestMessage> requests, Acknowledgment ack) {
    log.info("收到批量请求, 数量: {}", requests.size());
    
    // 批量处理
    List<ChatResultMessage> results = requests.stream()
            .map(this::processRequest)
            .collect(Collectors.toList());
    
    // 批量发送结果
    results.forEach(result -> requestProducer.sendResult(result));
    
    // 批量缓存结果
    results.forEach(result -> cacheResult(result.getRequestId(), result));
    
    // 批量确认
    ack.acknowledge();
    
    log.info("批量处理完成, 数量: {}", results.size());
}
```

### 5.2 优先级队列

JAVACopy

```
/**
 * 优先级分区策略
 */
public class PriorityPartitioner implements org.apache.kafka.clients.producer.Partitioner {
    
    @Override
    public int partition(String topic, String key, byte[] keyBytes, 
                        Object value, Cluster cluster) {
        
        if (key == null) {
            return 0;
        }
        
        // 从key中解析优先级
        // 这里假设requestId中包含优先级信息，实际使用可以从消息体中获取
        try {
            // 简单的哈希分区
            int partition = Math.abs(key.hashCode() % 6);
            
            // 可以根据优先级调整分区选择
            // 例如：高优先级路由到前两个分区
            return partition;
            
        } catch (Exception e) {
            return 0;
        }
    }
}
```

### 5.3 限流保护

JAVACopy

```
/**
 * 限流拦截器
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimiter rateLimiter;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                            Object handler) {
        
        String clientId = getClientId(request);
        RateLimitResult result = rateLimiter.tryAcquire(clientId);
        
        if (!result.isAllowed()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"retryAfter\":" + result.getRetryAfter() + "}"
            );
            return false;
        }
        
        return true;
    }
    
    private String getClientId(HttpServletRequest request) {
        // 从IP、API Key等维度识别客户端
        String ip = request.getRemoteAddr();
        String apiKey = request.getHeader("X-API-Key");
        return apiKey != null ? apiKey : ip;
    }
}
```

### 5.4 缓存预热

JAVACopy

```
/**
 * 热点数据缓存
 */
@Service
public class HotDataCacheService {
    
    @Scheduled(fixedRate = 60000)  // 每分钟执行
    public void warmupHotData() {
        // 预加载常用提示词
        // 预加载常用对话上下文
        // 预加载用户配置信息
    }
}
```

------

## 6. 部署方案

### 6.1 Docker Compose配置

YAMLCopy

```
version: '3.8'

services:
  # Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    hostname: zookeeper
    container_name: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - ai-chat-network
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      - zookeeper-logs:/var/lib/zookeeper/log
    healthcheck:
      test: ["CMD", "zkCli.sh", "ls", "/"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Kafka
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    hostname: kafka
    container_name: kafka
    ports:
      - "9092:9092"
    depends_on:
      zookeeper:
        condition: service_healthy
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG_DIRS: /var/lib/kafka/data
      KAFKA_MESSAGE_MAX_BYTES: 10485760
      KAFKA_REPLICATION_FACTOR: 1
      KAFKA_NUM_PARTITIONS: 6
    networks:
      - ai-chat-network
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-topics.sh", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 10s
      timeout: 10s
      retries: 10

  # Kafka UI（可选，用于管理）
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    ports:
      - "8081:8080"
    depends_on:
      kafka:
        condition: service_started
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    networks:
      - ai-chat-network

  # 应用服务
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: ai-chat-app
    ports:
      - "8000:8000"
    depends_on:
      kafka:
        condition: service_started
      mysql:
        condition: service_started
      mongodb:
        condition: service_started
      redis:
        condition: service_started
      qdrant:
        condition: service_started
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/mydocker
      - SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/chat_db
      - SPRING_REDIS_HOST=redis
      - AI_EMBEDDINGSTORE_QDRANT_HOST=qdrant
    networks:
      - ai-chat-network
    volumes:
      - ./logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # MySQL
  mysql:
    image: mysql:8.0
    container_name: mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: mydocker
    networks:
      - ai-chat-network
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MongoDB
  mongodb:
    image: mongo:latest
    container_name: mongodb
    ports:
      - "27017:27017"
    networks:
      - ai-chat-network
    volumes:
      - mongo-data:/data/db

  # Redis
  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    networks:
      - ai-chat-network
    volumes:
      - redis-data:/data

  # Qdrant向量数据库
  qdrant:
    image: qdrant/qdrant:latest
    container_name: qdrant
    ports:
      - "6333:6333"
      - "6334:6334"
    networks:
      - ai-chat-network
    volumes:
      - qdrant-data:/qdrant/storage

networks:
  ai-chat-network:
    driver: bridge

volumes:
  zookeeper-data:
  zookeeper-logs:
  kafka-data:
  mysql-data:
  mongo-data:
  redis-data:
  qdrant-data:
```

### 6.2 应用Dockerfile

DOCKERFILECopy

```
# Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests -f pom.xml

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# 设置时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 暴露端口
EXPOSE 8000

# 启动应用
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
```

### 6.3 监控配置

JAVACopy

```
/**
 * Kafka监控指标
 */
@Component
public class KafkaMetrics {
    
    private final MeterRegistry meterRegistry;
    
    @Autowired
    public KafkaMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @KafkaListener(topics = "ai-chat-request", groupId = "metrics-consumer")
    public void listenWithMetrics(ChatRequestMessage message) {
        // 请求计数
        meterRegistry.counter("ai.chat.request.received", 
                "messageType", message.getMessageType().name())
                .increment();
        
        // 处理耗时
        Timer.builder("ai.chat.request.processing")
                .tag("messageType", message.getMessageType().name())
                .register(meterRegistry)
                .record(() -> {
                    // 处理逻辑
                });
    }
}
```

**Prometheus监控配置：**

YAMLCopy

```
# prometheus.yml
scrape_configs:
  - job_name: 'ai-chat-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8000']
```

------

## 7. 迁移路径

### 7.1 平滑迁移策略

Copy

```
阶段1: 双写模式（第1周）
├── 新请求同时写入Kafka和原有处理流程
├── 逐步验证Kafka处理结果的正确性
├── 观察系统稳定性
└── 目标: 验证Kafka处理链路正确性

阶段2: 灰度发布（第2周）
├── 10%流量走Kafka异步处理
├── 90%流量走原有同步处理
├── 逐步提高比例
└── 目标: 验证性能和稳定性

阶段3: 完全切换（第3周）
├── 默认走Kafka异步处理
├── 保留同步接口作为降级方案
├── 监控关键指标
└── 目标: 线上全量运行

阶段4: 优化完善（第4周）
├── 性能调优
├── 监控告警完善
├── 文档完善
└── 目标: 稳定运行
```

### 7.2 降级方案

JAVACopy

```
/**
 * 降级拦截器
 */
@Aspect
@Component
public class FallbackAspect {
    
    @Autowired
    private ChatService syncChatService;
    
    @Around("@annotation(AsyncChat)")
    public Object asyncWithFallback(ProceedingJoinPoint joinPoint) {
        try {
            // 尝试异步处理
            return joinPoint.proceed();
        } catch (AsyncException e) {
            // 降级到同步处理
            log.warn("异步处理失败，降级到同步处理", e);
            
            // 从JoinPoint中获取参数
            Object[] args = joinPoint.getArgs();
            // 调用同步服务
            return syncChatService.chat(/* 获取参数 */);
        }
    }
}

/**
 * 同步降级Controller（保留原有接口）
 */
@RestController
@RequestMapping("/xiaozhi")
public class SyncChatController {
    
    @Autowired
    private ChatService chatService;
    
    /**
     * 保留原有同步接口作为降级方案
     */
    @PostMapping("/chat")
    public String chat(@RequestBody ChatForm chatForm) {
        return chatService.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }
}
```

### 7.3 回滚策略

| 场景      | 触发条件         | 回滚操作               |
| :-------- | :--------------- | :--------------------- |
| Kafka故障 | 无法连接Kafka    | 自动切换到同步处理     |
| 消息积压  | 积压超过10000条  | 降低消费速率，启用熔断 |
| 处理超时  | 平均响应超过10秒 | 增加消费者实例         |
| 数据错误  | 结果不一致率>1%  | 回退到同步处理         |

------

## 8. 预期效果

### 8.1 性能指标对比

| 指标             | 当前值   | 预期值     | 提升幅度     |
| :--------------- | :------- | :--------- | :----------- |
| **响应时间**     | 2-5秒    | 立即返回   | 99%↓         |
| **首次响应时间** | 2-5秒    | <100ms     | 95%↓         |
| **吞吐量**       | 10 req/s | 100+ req/s | **10倍↑**    |
| **并发能力**     | 15线程   | 无限制     | **弹性扩展** |
| **削峰能力**     | 无       | 10000+     | **弹性**     |
| **可用性**       | 单点     | 分布式     | **高可用**   |
| **平均负载率**   | 80%      | 30%        | **降低60%**  |

### 8.2 用户体验提升

| 场景     | 当前体验       | 优化后体验               |
| :------- | :------------- | :----------------------- |
| 正常请求 | 等待2-5秒      | 立即响应，异步推送结果   |
| 高峰时段 | 请求排队或拒绝 | 平滑处理，无感知         |
| 网络波动 | 请求失败       | 自动重试，结果可恢复     |
| 批量操作 | 串行等待       | 并行处理，总时间大幅降低 |

### 8.3 ROI分析

**成本投入：**

- Kafka集群: 3台中等配置服务器（约1500元/月）
- 开发成本: 约2人周
- 运维成本: 降低

**收益：**

- 吞吐量提升10倍，支持更多用户
- 用户体验提升，减少流失
- 系统稳定性增强，减少故障损失
- 资源利用率提升，降低基础设施成本

------

## 9. 风险与应对

### 9.1 技术风险

| 风险           | 可能性 | 影响 | 应对措施             |
| :------------- | :----- | :--- | :------------------- |
| Kafka集群故障  | 低     | 高   | 保留同步接口作为降级 |
| 消息丢失       | 低     | 高   | 配置ACKS=all，持久化 |
| 消息重复       | 中     | 中   | 幂等性处理，去重机制 |
| 消费者积压     | 中     | 中   | 监控告警，动态扩容   |
| 数据一致性问题 | 低     | 高   | 最终一致性补偿机制   |

### 9.2 业务风险

| 风险               | 可能性 | 影响 | 应对措施                 |
| :----------------- | :----- | :--- | :----------------------- |
| 用户不适应新接口   | 低     | 中   | 保留原有接口，逐步迁移   |
| 迁移期间系统不稳定 | 中     | 中   | 双写验证，灰度发布       |
| 旧数据迁移问题     | 低     | 中   | 数据兼容层，双写历史数据 |

### 9.3 监控告警

JAVACopy

```
/**
 * 告警规则配置
 */
@Configuration
public class AlertConfig {
    
    /**
     * 消息积压告警
     */
    @Bean
    public MeterHandler messageLagAlert() {
        return () -> {
            Map<String, Object> alerts = new HashMap<>();
            
            // 消费延迟告警（超过1000条）
            // 处理耗时告警（超过10秒）
            // 错误率告警（超过5%）
            
            return alerts;
        };
    }
}
```

------

## 10. 实施计划

### 10.1 开发任务清单

| 任务              | 优先级 | 预估工时 | 负责人 |
| :---------------- | :----- | :------- | :----- |
| Kafka环境搭建     | P0     | 1天      | 运维   |
| 依赖配置和配置项  | P0     | 0.5天    | 开发   |
| 消息实体类开发    | P0     | 0.5天    | 开发   |
| KafkaConfig配置类 | P0     | 1天      | 开发   |
| 生产者服务开发    | P0     | 1天      | 开发   |
| 消费者服务开发    | P0     | 2天      | 开发   |
| 异步控制器开发    | P0     | 1天      | 开发   |
| 结果查询服务      | P1     | 0.5天    | 开发   |
| 性能优化开发      | P1     | 1天      | 开发   |
| 监控指标接入      | P1     | 0.5天    | 开发   |
| Docker部署配置    | P0     | 0.5天    | 运维   |
| 测试用例编写      | P1     | 1天      | 测试   |
| 集成测试          | P1     | 1天      | 测试   |
| 性能压测          | P2     | 1天      | 测试   |
| 文档编写          | P2     | 0.5天    | 开发   |

### 10.2 里程碑计划

| 里程碑           | 时间点 | 交付物                  |
| :--------------- | :----- | :---------------------- |
| M1: 环境就绪     | 第1天  | Kafka集群就绪，配置完成 |
| M2: 核心开发完成 | 第7天  | 所有代码开发完成        |
| M3: 测试完成     | 第9天  | 测试报告，性能报告      |
| M4: 部署上线     | 第10天 | 生产环境部署完成        |
| M5: 稳定运行     | 第14天 | 监控报告，优化报告      |

### 10.3 验收标准

1. **功能验收：**
   - 异步提交接口正常返回requestId
   - 结果查询接口能正确返回处理结果
   - SSE推送接口能实时推送结果
   - 降级方案能正常切换
2. **性能验收：**
   - 吞吐量达到100 req/s以上
   - 异步接口响应时间<100ms
   - 消息无丢失
   - 无重复消息
3. **稳定性验收：**
   - 24小时稳定运行
   - 无内存泄漏
   - 错误率<0.1%

------

## 附录

### A. 配置项清单

| 配置项                         | 环境变量                | 默认值              | 说明         |
| :----------------------------- | :---------------------- | :------------------ | :----------- |
| spring.kafka.bootstrap-servers | KAFKA_BOOTSTRAP_SERVERS | localhost:9092      | Kafka地址    |
| kafka.topics.request           | -                       | ai-chat-request     | 请求Topic    |
| kafka.topics.result            | -                       | ai-chat-result      | 结果Topic    |
| kafka.consumer-groups.request  | -                       | ai-request-consumer | 请求消费者组 |

### B. API文档

#### B.1 异步提交接口

**POST /xiaozhi/chat/async**

Request:

JSONCopy

```
{
  "memoryId": 1234567890,
  "message": "请帮我翻译这段医学文献"
}
```

Response:

JSONCopy

```
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "message": "请求已提交，请使用requestId查询结果",
  "resultUrl": "/xiaozhi/result/550e8400-e29b-41d4-a716-446655440000",
  "streamUrl": "/xiaozhi/chat/stream/550e8400-e29b-41d4-a716-446655440000"
}
```

#### B.2 结果查询接口

**GET /xiaozhi/result/{requestId}**

Response (处理中):

JSONCopy

```
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "message": "请求正在处理中，请稍后重试",
  "retryAfter": 2000
}
```

Response (完成):

JSONCopy

```
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "result": "Here is the translation...",
  "processingTimeMs": 2500,
  "processedAt": "2026-01-15T10:30:02.500"
}
```

------

**文档结束**