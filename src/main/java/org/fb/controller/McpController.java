package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.mcpbean.JsonRpcRequest;
import org.fb.bean.mcpbean.JsonRpcResponse;
import org.fb.bean.mcpbean.ToolDefinition;
import org.fb.service.McpTool;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Tag(name = "智能对话")
@RequestMapping("/mcp")
public class McpController {

    private final List<McpTool> tools;

    public McpController(List<McpTool> tools) {
        this.tools = tools; // Spring 自动注入所有 McpTool Bean
    }

    /**
     * MCP服务根路径，用于健康检查
     * */
    @GetMapping
    @Operation(summary = "MCP服务健康检查")
    public Map<String, Object> healthCheck() {
        return Map.of(
            "status", "ok",
            "service", "MCP",
            "version", "1.0.0",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 返回所有工具定义
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
     * 执行工具调用（JSON-RPC 2.0）
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