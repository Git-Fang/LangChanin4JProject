package org.fb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.fb.monitor.BusinessMetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BusinessMetricsInterceptor implements HandlerInterceptor {

    @Autowired
    private BusinessMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    private final Map<String, Long> requestStartTimes = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null) {
            requestId = String.valueOf(System.nanoTime());
        }

        request.setAttribute("requestStartTime", System.currentTimeMillis());
        request.setAttribute("requestId", requestId);

        String uri = request.getRequestURI();
        if (uri.contains("/chat/") || uri.contains("/stream")) {
            metricsCollector.recordChatRequest(determineChatType(uri));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Long startTime = (Long) request.getAttribute("requestStartTime");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String uri = request.getRequestURI();

            if (uri.contains("/chat/") || uri.contains("/stream")) {
                meterRegistry.timer("http.request.duration",
                        "uri", uri,
                        "method", request.getMethod(),
                        "status", String.valueOf(response.getStatus())
                ).record(java.time.Duration.ofMillis(duration));
            }
        }

        if (ex != null) {
            metricsCollector.recordError();
            log.error("Request failed: {} - {}", request.getRequestURI(), ex.getMessage());
        }
    }

    private String determineChatType(String uri) {
        if (uri.contains("async")) return "async";
        if (uri.contains("stream")) return "streaming";
        if (uri.contains("http")) return "http";
        if (uri.contains("mcp")) return "mcp";
        return "default";
    }

    public void registerEmitter(String requestId, SseEmitter emitter) {
        activeEmitters.put(requestId, emitter);
        metricsCollector.recordSseConnection();

        emitter.onCompletion(() -> {
            activeEmitters.remove(requestId);
            metricsCollector.removeSseConnection();
        });

        emitter.onTimeout(() -> {
            activeEmitters.remove(requestId);
            metricsCollector.removeSseConnection();
        });

        emitter.onError(e -> {
            activeEmitters.remove(requestId);
            metricsCollector.removeSseConnection();
            metricsCollector.recordError();
        });
    }

    public void unregisterEmitter(String requestId) {
        SseEmitter emitter = activeEmitters.remove(requestId);
        if (emitter != null) {
            metricsCollector.removeSseConnection();
        }
    }
}
