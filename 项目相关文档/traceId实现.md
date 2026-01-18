# TraceId 链路追踪实现文档

## 一、实现原理

### 1.1 核心概念

- **TraceId**: 唯一标识一次完整请求链路，所有相关日志共享同一个 TraceId
- **SpanId**: 标识链路中的单个节点/操作
- **MDC (Mapped Diagnostic Context)**: Slf4j 提供的线程级日志上下文存储

### 1.2 整体架构

```
请求入口 (TraceIdFilter)
        │
        ▼
   生成 TraceId/SpanId
        │
        ▼
   存入 MDC
        │
        ├──► 同步处理 (直接使用 MDC)
        │
        └──► 异步处理 (MDCThreadPoolExecutor 传递 MDC)
                    │
                    ▼
              所有日志都含 TraceId
```

### 1.3 关键机制

1. **Filter 拦截**: 在请求入口处生成 TraceId，放入 MDC
2. **MDC 自动传递**: 包装线程池，异步任务执行前恢复 MDC 上下文
3. **日志格式**: Logback 配置 `%X{traceId}/%X{spanId}` 输出占位符

## 二、修改文件清单

### 2.1 新增文件 (4个)

| 文件 | 路径 | 功能说明 |
|------|------|----------|
| `TraceIdFilter.java` | `src/main/java/org/fb/config/` | 请求过滤器，生成并注入 TraceId/SpanId 到 MDC |
| `MDCThreadPoolExecutor.java` | `src/main/java/org/fb/config/` | 包装线程池，异步任务传递 MDC 上下文 |
| `ThreadPoolConfig.java` | `src/main/java/org/fb/config/` | 配置 MDC 线程池 Bean |
| `GlobalExceptionHandler.java` | `src/main/java/org/fb/config/` | 全局异常处理，确保异常日志也含 TraceId |

### 2.2 修改文件 (6个)

| 文件 | 路径 | 修改内容 |
|------|------|----------|
| `pom.xml` | 项目根目录 | 添加链路追踪依赖 |
| `application-docker.yml` | `src/main/resources/` | 添加 tracing 配置，日志格式增加 TraceId |
| `logback.xml` | `src/main/resources/` | 日志格式增加 `%X{traceId}/%X{spanId}` |
| `BusinessConstant.java` | `src/main/java/org/fb/constant/` | 新增 `THREAD_POOL_QUEUE_SIZE` 常量 |
| `DocumentImpl.java` | `src/main/java/org/fb/service/impl/` | 使用 `@Qualifier("mdcExecutorService")` 替换原生线程池 |
| `McpTestClientController.java` | `src/main/java/org/fb/controller/` | 使用 `@Qualifier("mdcExecutorService")` 替换原生线程池 |

## 三、核心代码说明

### 3.1 TraceIdFilter - 入口拦截

```java
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 生成 TraceId 和 SpanId
            String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            // 存入 MDC
            MDC.put(TRACE_ID, traceId);
            MDC.put(SPAN_ID, spanId);

            // 响应头返回，方便排查
            response.setHeader("X-Trace-Id", traceId);
            response.setHeader("X-Span-Id", spanId);

            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理 MDC
            MDC.remove(TRACE_ID);
            MDC.remove(SPAN_ID);
        }
    }
}
```

**要点**:
- `@Order(1)` 确保最先执行
- `finally` 块确保 MDC 一定被清理
- SSE 请求跳过，避免长连接阻塞

### 3.2 MDCThreadPoolExecutor - 异步上下文传递

```java
public class MDCThreadPoolExecutor extends ThreadPoolExecutor {

    @Override
    public void execute(Runnable command) {
        // 捕获当前线程的 MDC 上下文
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        super.execute(() -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                // 恢复 MDC 上下文到异步线程
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                command.run();
            } finally {
                // 恢复原线程的 MDC 状态
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        });
    }

    // submit() 方法类似处理...
}
```

**要点**:
- `MDC.getCopyOfContextMap()` 复制当前上下文
- 异步任务执行前恢复上下文
- `finally` 块确保上下文正确恢复和清理

### 3.3 日志格式配置

**logback.xml**:
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}/%X{spanId}] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

**application-docker.yml**:
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{traceId}/%X{spanId}] %-5level %logger{36} - %msg%n"
```

**要点**:
- `%X{traceId}` 从 MDC 读取 traceId 值
- `%X{spanId}` 从 MDC 读取 spanId 值

### 3.4 pom.xml 依赖

```xml
<!-- 链路追踪依赖 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.brave</groupId>
    <artifactId>brave-context-slf4j</artifactId>
    <version>6.0.1</version>
</dependency>
```

## 四、使用说明

### 4.1 日志示例

同步请求日志:
```
2026-01-14 15:30:45.123 [http-nio-8000-exec-1] [a1b2c3d4e5f6/7890abcd] INFO  org.fb.controller.ChatController - 收到聊天请求
```

CompletableFuture 异步日志:
```
2026-01-14 15:30:45.156 [pool-1-thread-2] [a1b2c3d4e5f6/def56789] DEBUG org.fb.service.impl.DocumentImpl - 文件向量化完成
```

异常日志:
```
2026-01-14 15:30:45.200 [http-nio-8000-exec-1] [a1b2c3d4e5f6/7890abcd] ERROR org.fb.config.GlobalExceptionHandler - 请求处理异常
```

### 4.2 响应头

所有 HTTP 响应会包含:
```
X-Trace-Id: a1b2c3d4e5f6
X-Span-Id: 7890abcd
```

### 4.3 过滤日志

使用 `grep` 或日志平台按 traceId 过滤:
```bash
# Linux
grep "a1b2c3d4e5f6" app.log

# ELK
traceId: "a1b2c3d4e5f6"
```

## 五、覆盖场景

| 场景 | 处理方式 |
|------|----------|
| **同步 HTTP 接口** | `TraceIdFilter` 拦截生成 TraceId |
| **CompletableFuture 异步** | `MDCThreadPoolExecutor` 传递 MDC 上下文 |
| **线程池任务** | `ThreadPoolConfig` 统一管理的 MDC 线程池 |
| **全局异常** | `GlobalExceptionHandler` 捕获异常时保留 TraceId |
| **SSE 长连接** | 跳过 `TraceIdFilter`，避免长连接阻塞 |

## 六、注意事项

1. **线程池替换**: 所有使用 `Executors.newXXX()` 的地方需替换为 `mdcExecutorService`
2. **MDC 清理**: `TraceIdFilter` 的 `finally` 块确保 MDC 被清理，防止内存泄漏
3. **SSE 排除**: SSE 连接使用长连接，不走 `TraceIdFilter`
4. **异步线程**: 确保 `CompletableFuture` 使用 `MDCThreadPoolExecutor`

## 七、后续扩展

如需更完善的链路追踪，可集成:
- **Zipkin**: 分布式链路追踪系统
- **SkyWalking**: 全链路 APM 工具
- **Jaeger**: CNCF 链路追踪项目
