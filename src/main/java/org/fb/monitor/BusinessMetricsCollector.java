package org.fb.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BusinessMetricsCollector {

    private final MeterRegistry meterRegistry;

    private final AtomicLong activeSseConnections = new AtomicLong(0);
    private final AtomicLong activeHttpChats = new AtomicLong(0);
    private final AtomicLong totalChatRequests = new AtomicLong(0);
    private final AtomicLong totalChatResponses = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> chatTypeCounts = new ConcurrentHashMap<>();

    private final AtomicLong activeMcpConnections = new AtomicLong(0);
    private final AtomicLong totalMcpRequests = new AtomicLong(0);
    private final AtomicLong totalMcpToolCalls = new AtomicLong(0);

    public BusinessMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("sse.connections.active", activeSseConnections, AtomicLong::get)
                .description("当前活跃的SSE连接数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Gauge.builder("http.chat.active", activeHttpChats, AtomicLong::get)
                .description("当前活跃的HTTP聊天会话数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Gauge.builder("mcp.connections.active", activeMcpConnections, AtomicLong::get)
                .description("当前活跃的MCP连接数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("chat.requests.total")
                .description("聊天请求总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("chat.responses.total")
                .description("聊天响应总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("chat.errors.total")
                .description("聊天错误总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("tokens.used.total")
                .description("使用的Token总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("mcp.requests.total")
                .description("MCP请求总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        Counter.builder("mcp.tool.calls.total")
                .description("MCP工具调用总数")
                .tag("application", "RAGTranslationApplication")
                .register(meterRegistry);

        for (String type : new String[]{"async", "streaming", "http", "mcp"}) {
            chatTypeCounts.put(type, new AtomicLong(0));
            Counter.builder("chat.requests.by.type")
                    .description("按类型分类的聊天请求数")
                    .tag("application", "RAGTranslationApplication")
                    .tag("chat_type", type)
                    .register(meterRegistry);
        }

        for (String intent : new String[]{"medical", "translation", "term_extraction", "sql_transfer", "general"}) {
            Counter.builder("chat.requests.by.intent")
                    .description("按意图分类的聊天请求数")
                    .tag("application", "RAGTranslationApplication")
                    .tag("intent", intent)
                    .register(meterRegistry);

            Timer.builder("chat.processing.by.intent")
                    .description("按意图分类的聊天处理耗时")
                    .tag("application", "RAGTranslationApplication")
                    .tag("intent", intent)
                    .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                    .register(meterRegistry);
        }

        for (String service : new String[]{"doctor_agent", "translator_service", "term_extraction_agent", "nl2sql_agent", "chat_assistant"}) {
            Timer.builder("business.service.duration")
                    .description("各业务服务处理耗时")
                    .tag("application", "RAGTranslationApplication")
                    .tag("service", service)
                    .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                    .register(meterRegistry);
        }

        Timer.builder("intent.recognition.duration")
                .description("意图识别耗时")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        Timer.builder("sse.message.duration")
                .description("SSE消息发送耗时")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        DistributionSummary.builder("chat.response.length")
                .description("聊天响应长度分布")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        DistributionSummary.builder("tokens.used.per.request")
                .description("每次请求使用的Token数分布")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        Timer.builder("database.operation.duration")
                .description("数据库操作耗时")
                .tag("operation", "save_chat_info")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        Timer.builder("vector.store.duration")
                .description("向量存储操作耗时")
                .tag("operation", "embedding_save")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void recordSseConnection() {
        activeSseConnections.incrementAndGet();
    }

    public void removeSseConnection() {
        activeSseConnections.decrementAndGet();
    }

    public void recordSseConnectionDuration(long durationMs) {
        Timer.builder("sse.connection.duration")
                .description("SSE连接持续时间")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordHttpChatStart() {
        activeHttpChats.incrementAndGet();
    }

    public void recordHttpChatEnd() {
        activeHttpChats.decrementAndGet();
    }

    public void recordHttpChatDuration(long durationMs) {
        Timer.builder("http.chat.duration")
                .description("HTTP聊天响应时间")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordChatRequest(String chatType) {
        totalChatRequests.incrementAndGet();
        AtomicLong counter = chatTypeCounts.get(chatType);
        if (counter != null) {
            counter.incrementAndGet();
        }
        meterRegistry.counter("chat.requests.total", "chat_type", chatType).increment();
    }

    public void recordChatRequestByIntent(String intent) {
        meterRegistry.counter("chat.requests.by.intent", "intent", intent).increment();
    }

    public void recordChatResponse() {
        totalChatResponses.incrementAndGet();
        meterRegistry.counter("chat.responses.total").increment();
    }

    public void recordError() {
        totalErrors.incrementAndGet();
        meterRegistry.counter("chat.errors.total").increment();
    }

    public void recordError(String errorType) {
        totalErrors.incrementAndGet();
        meterRegistry.counter("chat.errors.by.type", "error_type", errorType).increment();
    }

    public void recordTokensUsed(long count) {
        totalTokensUsed.addAndGet(count);
        meterRegistry.counter("tokens.used.total").increment();
        DistributionSummary.builder("tokens.used.per.request")
                .register(meterRegistry)
                .record(count);
    }

    public void recordChatProcessingDuration(long durationMs) {
        Timer.builder("chat.processing.duration")
                .description("聊天处理总耗时")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordChatProcessingDurationByIntent(long durationMs, String intent) {
        Timer.builder("chat.processing.by.intent")
                .tag("intent", intent)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordIntentRecognitionDuration(long durationMs) {
        Timer.builder("intent.recognition.duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordBusinessServiceDuration(long durationMs, String serviceName) {
        Timer.builder("business.service.duration")
                .tag("service", serviceName)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSseMessageDuration(long durationMs) {
        Timer.builder("sse.message.duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordChatResponseLength(long length) {
        DistributionSummary.builder("chat.response.length")
                .register(meterRegistry)
                .record(length);
    }

    public void recordDatabaseOperationDuration(long durationMs) {
        Timer.builder("database.operation.duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordVectorStoreOperationDuration(long durationMs) {
        Timer.builder("vector.store.duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordMcpConnection() {
        activeMcpConnections.incrementAndGet();
    }

    public void removeMcpConnection() {
        activeMcpConnections.decrementAndGet();
    }

    public void recordMcpRequest() {
        totalMcpRequests.incrementAndGet();
        meterRegistry.counter("mcp.requests.total").increment();
    }

    public void recordMcpToolCall() {
        totalMcpToolCalls.incrementAndGet();
        meterRegistry.counter("mcp.tool.calls.total").increment();
    }

    public void recordMcpToolCallDuration(long durationMs, String toolName) {
        Timer.builder("mcp.tool.call.duration")
                .tag("tool", toolName)
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSseMessagesSent(long count) {
        meterRegistry.counter("sse.messages.sent.total").increment(count);
    }

    public void recordSseChunksSent(long count) {
        meterRegistry.counter("sse.chunks.sent.total").increment(count);
    }

    public long getActiveSseConnections() {
        return activeSseConnections.get();
    }

    public long getActiveHttpChats() {
        return activeHttpChats.get();
    }

    public long getActiveMcpConnections() {
        return activeMcpConnections.get();
    }

    public long getTotalChatRequests() {
        return totalChatRequests.get();
    }

    public long getTotalChatResponses() {
        return totalChatResponses.get();
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed.get();
    }

    public long getTotalMcpRequests() {
        return totalMcpRequests.get();
    }

    public long getTotalMcpToolCalls() {
        return totalMcpToolCalls.get();
    }

    public long getChatTypeCount(String chatType) {
        AtomicLong counter = chatTypeCounts.get(chatType);
        return counter != null ? counter.get() : 0;
    }
}
