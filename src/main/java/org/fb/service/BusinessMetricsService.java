package org.fb.service;

import org.fb.monitor.BusinessMetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BusinessMetricsService {

    @Autowired
    private BusinessMetricsCollector metricsCollector;

    public void recordSseConnectionCreated(String sessionId) {
        metricsCollector.recordSseConnection();
    }

    public void recordSseConnectionClosed(String sessionId) {
        metricsCollector.removeSseConnection();
    }

    public void recordSseConnectionDuration(String sessionId, long durationMs) {
        metricsCollector.recordSseConnectionDuration(durationMs);
    }

    public void recordHttpChatStarted(String requestId) {
        metricsCollector.recordHttpChatStart();
    }

    public void recordHttpChatCompleted(String requestId, long durationMs) {
        metricsCollector.recordHttpChatEnd();
        metricsCollector.recordHttpChatDuration(durationMs);
    }

    public void recordChatRequest(String requestId, String chatType) {
        metricsCollector.recordChatRequest(chatType);
    }

    public void recordChatRequestByIntent(String requestId, String intent) {
        metricsCollector.recordChatRequestByIntent(intent);
    }

    public void recordChatResponse(String requestId) {
        metricsCollector.recordChatResponse();
    }

    public void recordError(String requestId) {
        metricsCollector.recordError();
    }

    public void recordError(String requestId, String errorType) {
        metricsCollector.recordError(errorType);
    }

    public void recordTokensUsed(String requestId, long tokenCount) {
        metricsCollector.recordTokensUsed(tokenCount);
    }

    public void recordChatProcessingDuration(String requestId, long durationMs) {
        metricsCollector.recordChatProcessingDuration(durationMs);
    }

    public void recordChatProcessingDurationByIntent(String requestId, long durationMs, String intent) {
        metricsCollector.recordChatProcessingDurationByIntent(durationMs, intent);
    }

    public void recordIntentRecognitionDuration(String requestId, long durationMs) {
        metricsCollector.recordIntentRecognitionDuration(durationMs);
    }

    public void recordBusinessServiceDuration(String requestId, long durationMs, String serviceName) {
        metricsCollector.recordBusinessServiceDuration(durationMs, serviceName);
    }

    public void recordSseMessageDuration(String requestId, long durationMs) {
        metricsCollector.recordSseMessageDuration(durationMs);
    }

    public void recordChatResponseLength(String requestId, long length) {
        metricsCollector.recordChatResponseLength(length);
    }

    public void recordDatabaseOperationDuration(String requestId, long durationMs) {
        metricsCollector.recordDatabaseOperationDuration(durationMs);
    }

    public void recordVectorStoreOperationDuration(String requestId, long durationMs) {
        metricsCollector.recordVectorStoreOperationDuration(durationMs);
    }

    public void recordMcpConnection(String sessionId) {
        metricsCollector.recordMcpConnection();
    }

    public void removeMcpConnection(String sessionId) {
        metricsCollector.removeMcpConnection();
    }

    public void recordMcpRequest(String sessionId) {
        metricsCollector.recordMcpRequest();
    }

    public void recordMcpToolCall(String sessionId) {
        metricsCollector.recordMcpToolCall();
    }

    public void recordMcpToolCallDuration(String sessionId, long durationMs, String toolName) {
        metricsCollector.recordMcpToolCallDuration(durationMs, toolName);
    }

    public void recordSseMessagesSent(String requestId, long count) {
        metricsCollector.recordSseMessagesSent(count);
    }

    public void recordSseChunksSent(String requestId, long count) {
        metricsCollector.recordSseChunksSent(count);
    }

    public long getActiveSseConnections() {
        return metricsCollector.getActiveSseConnections();
    }

    public long getActiveHttpChats() {
        return metricsCollector.getActiveHttpChats();
    }

    public long getActiveMcpConnections() {
        return metricsCollector.getActiveMcpConnections();
    }

    public long getTotalChatRequests() {
        return metricsCollector.getTotalChatRequests();
    }

    public long getTotalChatResponses() {
        return metricsCollector.getTotalChatResponses();
    }

    public long getTotalErrors() {
        return metricsCollector.getTotalErrors();
    }

    public long getTotalTokensUsed() {
        return metricsCollector.getTotalTokensUsed();
    }

    public long getTotalMcpRequests() {
        return metricsCollector.getTotalMcpRequests();
    }

    public long getTotalMcpToolCalls() {
        return metricsCollector.getTotalMcpToolCalls();
    }
}
