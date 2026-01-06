package org.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.mcpbean.McpMessage;
import org.fb.service.McpSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

@RestController
@Tag(name = "MCP SSE服务")
@RequestMapping("/mcp")
public class McpSseController {
    private static final Logger logger = LoggerFactory.getLogger(McpSseController.class);

    private final McpSseService mcpSseService;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpSseController(McpSseService mcpSseService, ObjectMapper objectMapper) {
        this.mcpSseService = mcpSseService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立SSE连接")
    public SseEmitter connectSse(HttpServletRequest request, @RequestParam(required = false) String sessionId) {
        String headerSessionId = request.getHeader("X-Session-ID");
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = headerSessionId;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        logger.info("建立SSE连接: sessionId={}, clientIp={}", sessionId, request.getRemoteAddr());

        SseEmitter emitter = mcpSseService.createEmitter(sessionId);

        return emitter;
    }

    @PostMapping("/messages")
    @Operation(summary = "发送MCP消息")
    public Mono<Void> sendMessage(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @RequestBody McpMessage message,
            HttpServletRequest request) {
        
        String finalSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : UUID.randomUUID().toString();

        logger.info("接收MCP消息: sessionId={}, method={}", finalSessionId, message.getMethod());

        return mcpSseService.handleMessage(finalSessionId, message)
            .doOnError(error -> logger.error("处理MCP消息失败: sessionId={}, error={}", finalSessionId, error.getMessage(), error));
    }

    @GetMapping("/health")
    @Operation(summary = "MCP SSE服务健康检查")
    public Mono<Map<String, Object>> healthCheck() {
        return Mono.just(Map.of(
            "status", "ok",
            "service", "MCP SSE",
            "version", "1.0.0",
            "transport", "SSE",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
