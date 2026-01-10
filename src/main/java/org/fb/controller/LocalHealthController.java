package org.fb.controller;

import io.qdrant.client.QdrantClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController("/")
@Tag(name = "本地开发")
@ConditionalOnProperty(name = "app.local-mode", havingValue = "true", matchIfMissing = false)
public class LocalHealthController {
    private static final Logger logger = LoggerFactory.getLogger(LocalHealthController.class);

    @GetMapping("/mcp/exec")
    @Operation(summary = "本地MCP执行（兼容模式）")
    public ResponseEntity<Object> localMcpExec(@RequestBody(required = false) Map<String, Object> request) {
        logger.info("MCP 本地模式 - 模拟响应");

        // 返回一个模拟响应，避免前端出错
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", request != null ? request.get("id") : "1");
        response.put("result", Map.of(
            "content", "[本地模式] MCP服务暂不可用，请配置API密钥并确保服务正常运行。",
            "isError", false
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/local/status")
    @Operation(summary = "本地服务状态")
    public ResponseEntity<Map<String, Object>> localStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", "local");
        status.put("status", "running");
        status.put("mcp_enabled", false);
        status.put("qdrant_connected", false);
        status.put("translation_enabled", true);
        status.put("message", "应用正在本地模式下运行 \uD83D\uDE80");

        return ResponseEntity.ok(status);
    }

    @GetMapping("/test")
    @Operation(summary = "测试接口")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "RAG Translation System is running!");
        response.put("service", "RAG Translation");
        response.put("version", "1.0.0");
        response.put("error", "No critical errors - some external services may be unavailable");

        return ResponseEntity.ok(response);
    }
}