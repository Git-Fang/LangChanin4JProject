package org.fb.monitor;

import lombok.extern.slf4j.Slf4j;
import org.fb.service.BusinessMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class McpMetricsMonitor {

    @Autowired
    private BusinessMetricsService metricsService;

    private final Map<String, McpSessionInfo> sessions = new ConcurrentHashMap<>();

    public SseEmitter createMonitoredEmitter(String sessionId) {
        long startTime = System.currentTimeMillis();

        SseEmitter emitter = new SseEmitter(300000L);

        McpSessionInfo sessionInfo = new McpSessionInfo(sessionId, startTime);
        sessions.put(sessionId, sessionInfo);

        metricsService.recordMcpConnection(sessionId);

        emitter.onCompletion(() -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("completed");
            sessionInfo.setDuration(System.currentTimeMillis() - startTime);
            metricsService.removeMcpConnection(sessionId);
            log.info("MCP会话完成, sessionId: {}, duration: {}ms", sessionId, sessionInfo.getDuration());
        });

        emitter.onTimeout(() -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("timeout");
            sessionInfo.setDuration(System.currentTimeMillis() - startTime);
            metricsService.removeMcpConnection(sessionId);
            log.info("MCP会话超时, sessionId: {}, duration: {}ms", sessionId, sessionInfo.getDuration());
        });

        emitter.onError(e -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("error");
            sessionInfo.setErrorMessage(e.getMessage());
            sessionInfo.setDuration(System.currentTimeMillis() - startTime);
            metricsService.removeMcpConnection(sessionId);
            log.error("MCP会话错误, sessionId: {}, error: {}", sessionId, e.getMessage());
        });

        log.info("创建监控的MCP会话, sessionId: {}", sessionId);
        return emitter;
    }

    public void recordToolCall(String sessionId, String toolName, long durationMs) {
        McpSessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.incrementToolCalls();
            session.addToolCallDuration(durationMs);
        }
        metricsService.recordMcpToolCall(sessionId);
        metricsService.recordMcpToolCallDuration(sessionId, durationMs, toolName);
    }

    public void recordMessageSent(String sessionId) {
        McpSessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.incrementMessagesSent();
        }
    }

    public void recordMessageReceived(String sessionId) {
        McpSessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.incrementMessagesReceived();
        }
    }

    public void recordRequestProcessed(String sessionId) {
        metricsService.recordMcpRequest(sessionId);
        McpSessionInfo session = sessions.get(sessionId);
        if (session != null) {
            session.incrementRequestsProcessed();
        }
    }

    public Map<String, McpSessionInfo> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    public McpSessionInfo getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public static class McpSessionInfo {
        private final String sessionId;
        private final long startTime;
        private volatile long endTime;
        private volatile String status;
        private volatile AtomicLong toolCalls = new AtomicLong(0);
        private volatile AtomicLong messagesSent = new AtomicLong(0);
        private volatile AtomicLong messagesReceived = new AtomicLong(0);
        private volatile AtomicLong requestsProcessed = new AtomicLong(0);
        private volatile long totalToolCallDuration;
        private volatile String errorMessage;
        private volatile long duration;

        public McpSessionInfo(String sessionId, long startTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
            this.status = "active";
        }

        public long getDuration() {
            return duration > 0 ? duration : System.currentTimeMillis() - startTime;
        }

        public void incrementToolCalls() {
            toolCalls.incrementAndGet();
        }

        public void addToolCallDuration(long durationMs) {
            totalToolCallDuration += durationMs;
        }

        public void incrementMessagesSent() {
            messagesSent.incrementAndGet();
        }

        public void incrementMessagesReceived() {
            messagesReceived.incrementAndGet();
        }

        public void incrementRequestsProcessed() {
            requestsProcessed.incrementAndGet();
        }

        public String getSessionId() { return sessionId; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getToolCalls() { return toolCalls.get(); }
        public long getMessagesSent() { return messagesSent.get(); }
        public long getMessagesReceived() { return messagesReceived.get(); }
        public long getRequestsProcessed() { return requestsProcessed.get(); }
        public long getTotalToolCallDuration() { return totalToolCallDuration; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getErrorMessage() { return errorMessage; }
        public void setDuration(long duration) { this.duration = duration; }
    }
}
