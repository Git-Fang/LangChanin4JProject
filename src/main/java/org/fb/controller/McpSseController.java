package org.fb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.mcpbean.JsonRpcRequest;
import org.fb.bean.mcpbean.JsonRpcResponse;
import org.fb.bean.mcpbean.McpMessage;
import org.fb.bean.mcpbean.ToolDefinition;
import org.fb.service.McpSseService;
import org.fb.service.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@Tag(name = "MCP SSE服务")
@RequestMapping("/mcp")
public class McpSseController {
    private static final Logger logger = LoggerFactory.getLogger(McpSseController.class);

    private final McpSseService mcpSseService;
    private final ObjectMapper objectMapper;
    private final List<McpTool> tools;

    @Autowired
    public McpSseController(McpSseService mcpSseService, ObjectMapper objectMapper, List<McpTool> tools) {
        this.mcpSseService = mcpSseService;
        this.objectMapper = objectMapper;
        this.tools = tools;
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

    /**
     * MCP服务根路径，用于健康检查（HTTP兼容接口）
     * */
    @GetMapping
    @Operation(summary = "MCP服务健康检查")
    public Map<String, Object> rootHealthCheck() {
        return Map.of(
            "status", "ok",
            "service", "MCP",
            "version", "1.0.0",
            "transport", "HTTP/SSE",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 返回所有工具定义（HTTP兼容接口）
     * */
    @GetMapping("/tools")
    @Operation(summary = "获取所有MCP Tools")
    public List<ToolDefinition> listTools() {
        return tools.stream()
            .map(tool -> ToolDefinition.of(
                tool.getName(),
                tool.getDescription(),
                tool.getInputSchema(),
                tool.getOutputSchema()
            ))
            .toList();
    }

    /**
     * 执行工具调用（JSON-RPC 2.0）（HTTP兼容接口）
     * */
    @PostMapping("/exec")
    @Operation(summary = "MCP工具执行")
    public Mono<JsonRpcResponse> callTool(@RequestBody JsonRpcRequest request) {
        if (!"2.0".equals(request.getJsonrpc())) {
            return Mono.just(JsonRpcResponse.error("Only JSON-RPC 2.0 supported", request.getId()));
        }

        Optional<McpTool> toolOpt = tools.stream()
            .filter(t -> t.getName().equals(request.getMethod()))
            .findFirst();
        if (toolOpt.isEmpty()) {
            return Mono.just(JsonRpcResponse.error("Tool not found: " + request.getMethod(), request.getId()));
        }

        return toolOpt.get().execute(request.getParams())
            .map(result -> JsonRpcResponse.success(result, request.getId()))
            .onErrorReturn(JsonRpcResponse.error("Execution failed", request.getId()));
    }
}
