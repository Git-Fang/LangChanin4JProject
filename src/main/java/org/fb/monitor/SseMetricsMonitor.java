package org.fb.monitor;

import lombok.extern.slf4j.Slf4j;
import org.fb.config.BusinessMetricsInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SseMetricsMonitor {

    @Autowired
    private BusinessMetricsInterceptor metricsInterceptor;

    private final ConcurrentHashMap<String, SseSessionInfo> sessions = new ConcurrentHashMap<>();

    public SseEmitter createMonitoredEmitter(String requestId) {
        long startTime = System.currentTimeMillis();

        SseEmitter emitter = new SseEmitter(600000L);

        SseSessionInfo sessionInfo = new SseSessionInfo(requestId, startTime);
        sessions.put(requestId, sessionInfo);

        metricsInterceptor.registerEmitter(requestId, emitter);

        emitter.onCompletion(() -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("completed");
            metricsInterceptor.unregisterEmitter(requestId);
            log.info("SSE会话完成, requestId: {}, duration: {}ms", requestId, sessionInfo.getDuration());
        });

        emitter.onTimeout(() -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("timeout");
            metricsInterceptor.unregisterEmitter(requestId);
            log.info("SSE会话超时, requestId: {}, duration: {}ms", requestId, sessionInfo.getDuration());
        });

        emitter.onError(e -> {
            sessionInfo.setEndTime(System.currentTimeMillis());
            sessionInfo.setStatus("error");
            sessionInfo.setErrorMessage(e.getMessage());
            metricsInterceptor.unregisterEmitter(requestId);
            log.error("SSE会话错误, requestId: {}, error: {}", requestId, e.getMessage());
        });

        log.info("创建监控的SSE会话, requestId: {}", requestId);
        return emitter;
    }

    public void recordTokenUsage(String requestId, long tokenCount) {
        SseSessionInfo session = sessions.get(requestId);
        if (session != null) {
            session.addTokens(tokenCount);
        }
    }

    public void recordMessageSent(String requestId) {
        SseSessionInfo session = sessions.get(requestId);
        if (session != null) {
            session.incrementMessagesSent();
        }
    }

    public Map<String, SseSessionInfo> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    public static class SseSessionInfo {
        private final String requestId;
        private final long startTime;
        private volatile long endTime;
        private volatile String status;
        private volatile long tokensUsed;
        private volatile AtomicLong messagesSent = new AtomicLong(0);
        private volatile String errorMessage;

        public SseSessionInfo(String requestId, long startTime) {
            this.requestId = requestId;
            this.startTime = startTime;
            this.status = "active";
        }

        public long getDuration() {
            return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
        }

        public void addTokens(long count) {
            this.tokensUsed += count;
        }

        public void incrementMessagesSent() {
            this.messagesSent.incrementAndGet();
        }

        public String getRequestId() { return requestId; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getTokensUsed() { return tokensUsed; }
        public long getMessagesSent() { return messagesSent.get(); }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getErrorMessage() { return errorMessage; }
    }
}
