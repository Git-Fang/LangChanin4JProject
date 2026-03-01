package org.fb.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import org.fb.monitor.BusinessMetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/business-metrics")
public class BusinessMetricsController {

    @Autowired
    private BusinessMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("activeSseConnections", metricsCollector.getActiveSseConnections());
        summary.put("activeHttpChats", metricsCollector.getActiveHttpChats());
        summary.put("activeMcpConnections", metricsCollector.getActiveMcpConnections());
        summary.put("totalChatRequests", metricsCollector.getTotalChatRequests());
        summary.put("totalChatResponses", metricsCollector.getTotalChatResponses());
        summary.put("totalErrors", metricsCollector.getTotalErrors());
        summary.put("totalTokensUsed", metricsCollector.getTotalTokensUsed());
        summary.put("totalMcpRequests", metricsCollector.getTotalMcpRequests());
        summary.put("totalMcpToolCalls", metricsCollector.getTotalMcpToolCalls());

        Map<String, Double> timerStats = new HashMap<>();
        timerStats.put("sseConnectionAvg", getTimerMean("sse.connection.duration"));
        timerStats.put("sseConnectionMax", getTimerMax("sse.connection.duration"));
        timerStats.put("httpChatAvg", getTimerMean("http.chat.duration"));
        timerStats.put("httpChatMax", getTimerMax("http.chat.duration"));
        timerStats.put("chatProcessingAvg", getTimerMean("chat.processing.duration"));
        timerStats.put("chatProcessingMax", getTimerMax("chat.processing.duration"));
        timerStats.put("intentRecognitionAvg", getTimerMean("intent.recognition.duration"));
        timerStats.put("intentRecognitionMax", getTimerMax("intent.recognition.duration"));
        timerStats.put("sseMessageAvg", getTimerMean("sse.message.duration"));
        timerStats.put("sseMessageMax", getTimerMax("sse.message.duration"));
        timerStats.put("databaseOperationAvg", getTimerMean("database.operation.duration"));
        timerStats.put("databaseOperationMax", getTimerMax("database.operation.duration"));
        timerStats.put("vectorStoreAvg", getTimerMean("vector.store.duration"));
        timerStats.put("vectorStoreMax", getTimerMax("vector.store.duration"));

        summary.put("timerStats", timerStats);

        Map<String, Long> chatTypeCounts = new HashMap<>();
        chatTypeCounts.put("async", metricsCollector.getChatTypeCount("async"));
        chatTypeCounts.put("streaming", metricsCollector.getChatTypeCount("streaming"));
        chatTypeCounts.put("http", metricsCollector.getChatTypeCount("http"));
        chatTypeCounts.put("mcp", metricsCollector.getChatTypeCount("mcp"));
        summary.put("chatTypeCounts", chatTypeCounts);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/business-services")
    public ResponseEntity<Map<String, Object>> getBusinessServiceMetrics() {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Double> serviceStats = new HashMap<>();

        String[] services = {"doctor_agent", "translator_service", "term_extraction_agent", "nl2sql_agent", "chat_assistant"};
        for (String service : services) {
            double avg = getTimerMeanByTag("business.service.duration", "service", service);
            double max = getTimerMaxByTag("business.service.duration", "service", service);
            double count = getTimerCountByTag("business.service.duration", "service", service);
            serviceStats.put(service + "Avg", avg);
            serviceStats.put(service + "Max", max);
            serviceStats.put(service + "Count", count);
        }

        summary.put("serviceStats", serviceStats);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/intent-recognition")
    public ResponseEntity<Map<String, Object>> getIntentRecognitionMetrics() {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Long> intentCounts = new HashMap<>();
        Map<String, Double> intentDurations = new HashMap<>();

        String[] intents = {"medical", "translation", "term_extraction", "sql_transfer", "general"};
        for (String intent : intents) {
            intentCounts.put(intent, (long) getCounterCountByTag("chat.requests.by.intent", "intent", intent));
            intentDurations.put(intent + "Avg", getTimerMeanByTag("chat.processing.by.intent", "intent", intent));
            intentDurations.put(intent + "Max", getTimerMaxByTag("chat.processing.by.intent", "intent", intent));
        }

        summary.put("intentCounts", intentCounts);
        summary.put("intentDurations", intentDurations);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/tokens-usage")
    public ResponseEntity<Map<String, Object>> getTokenUsageMetrics() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalTokensUsed", metricsCollector.getTotalTokensUsed());

        DistributionSummary summaryObj = meterRegistry.find("tokens.used.per.request").summary();
        if (summaryObj != null) {
            summary.put("tokensPerRequestAvg", summaryObj.mean());
            summary.put("tokensPerRequestMax", summaryObj.max());
            summary.put("tokensPerRequestTotal", summaryObj.totalAmount());
        }

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/sse-metrics")
    public ResponseEntity<Map<String, Object>> getSseMetrics() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("activeConnections", metricsCollector.getActiveSseConnections());

        summary.put("connectionAvgMs", getTimerMean("sse.connection.duration"));
        summary.put("connectionMaxMs", getTimerMax("sse.connection.duration"));
        summary.put("connectionCount", getTimerCount("sse.connection.duration"));

        summary.put("messageAvgMs", getTimerMean("sse.message.duration"));
        summary.put("messageMaxMs", getTimerMax("sse.message.duration"));

        summary.put("totalChunksSent", getCounterCount("sse.chunks.sent.total"));
        summary.put("totalMessagesSent", getCounterCount("sse.messages.sent.total"));

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/mcp-metrics")
    public ResponseEntity<Map<String, Object>> getMcpMetrics() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("activeConnections", metricsCollector.getActiveMcpConnections());
        summary.put("totalRequests", metricsCollector.getTotalMcpRequests());
        summary.put("totalToolCalls", metricsCollector.getTotalMcpToolCalls());

        Map<String, Double> toolCallStats = new HashMap<>();
        toolCallStats.put("avgMs", getTimerMean("mcp.tool.call.duration"));
        toolCallStats.put("maxMs", getTimerMax("mcp.tool.call.duration"));
        toolCallStats.put("count", getTimerCount("mcp.tool.call.duration"));
        summary.put("toolCallStats", toolCallStats);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/errors")
    public ResponseEntity<Map<String, Object>> getErrorMetrics() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalErrors", metricsCollector.getTotalErrors());

        Map<String, Long> errorByType = new HashMap<>();
        String[] errorTypes = {"doctor_agent_error", "translator_service_error", "term_extraction_agent_error",
                              "nl2sql_agent_error", "sse_timeout", "sse_error", "redis_save_error", "parse_request_error", "request_not_found"};
        for (String errorType : errorTypes) {
            errorByType.put(errorType, (long) getCounterCountByTag("chat.errors.by.type", "error_type", errorType));
        }
        summary.put("errorsByType", errorByType);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("metricsCollector", "active");
        return ResponseEntity.ok(health);
    }

    private double getTimerMean(String meterName) {
        Timer timer = meterRegistry.find(meterName).timer();
        return timer != null ? timer.mean(TimeUnit.MILLISECONDS) : 0.0;
    }

    private double getTimerMax(String meterName) {
        Timer timer = meterRegistry.find(meterName).timer();
        return timer != null ? timer.max(TimeUnit.MILLISECONDS) : 0.0;
    }

    private double getTimerCount(String meterName) {
        Timer timer = meterRegistry.find(meterName).timer();
        return timer != null ? timer.count() : 0.0;
    }

    private double getTimerMeanByTag(String meterName, String tagKey, String tagValue) {
        Collection<Timer> timers = meterRegistry.find(meterName).tag(tagKey, tagValue).timers();
        if (timers.isEmpty()) return 0.0;
        return timers.iterator().next().mean(TimeUnit.MILLISECONDS);
    }

    private double getTimerMaxByTag(String meterName, String tagKey, String tagValue) {
        Collection<Timer> timers = meterRegistry.find(meterName).tag(tagKey, tagValue).timers();
        if (timers.isEmpty()) return 0.0;
        return timers.iterator().next().max(TimeUnit.MILLISECONDS);
    }

    private double getTimerCountByTag(String meterName, String tagKey, String tagValue) {
        Collection<Timer> timers = meterRegistry.find(meterName).tag(tagKey, tagValue).timers();
        if (timers.isEmpty()) return 0.0;
        return timers.iterator().next().count();
    }

    private double getCounterCount(String meterName) {
        Counter counter = meterRegistry.find(meterName).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double getCounterCountByTag(String meterName, String tagKey, String tagValue) {
        Collection<Counter> counters = meterRegistry.find(meterName).tag(tagKey, tagValue).counters();
        if (counters.isEmpty()) return 0.0;
        return counters.iterator().next().count();
    }
}
