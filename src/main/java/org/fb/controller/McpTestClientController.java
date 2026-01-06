package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@Tag(name = "MCP测试客户端")
@RequestMapping("/mcp/test")
public class McpTestClientController {
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @GetMapping("/sse-demo")
    @Operation(summary = "SSE MCP服务演示")
    public Map<String, Object> sseDemo() {
        String baseUrl = "http://localhost:8080/mcp";
        String sessionId = UUID.randomUUID().toString();

        try {
            Map<String, Object> result = Map.of(
                "sessionId", sessionId,
                "steps", Map.of(
                    "1. 建立SSE连接", "GET " + baseUrl + "/sse",
                    "2. 发送初始化请求", "POST " + baseUrl + "/messages",
                    "3. 获取工具列表", "POST " + baseUrl + "/messages (tools/list)",
                    "4. 调用工具", "POST " + baseUrl + "/messages (tools/call)",
                    "5. 关闭连接", "自动关闭或发送shutdown消息"
                ),
                "exampleInitialize", Map.of(
                    "jsonrpc", "2.0",
                    "id", "1",
                    "method", "initialize",
                    "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                            "name", "TestClient",
                            "version", "1.0.0"
                        )
                    )
                ),
                "exampleToolsList", Map.of(
                    "jsonrpc", "2.0",
                    "id", "2",
                    "method", "tools/list"
                ),
                "exampleToolCall", Map.of(
                    "jsonrpc", "2.0",
                    "id", "3",
                    "method", "tools/call",
                    "params", Map.of(
                        "name", "composite_intelligent_agent",
                        "arguments", Map.of(
                            "userMessage", "你好，请介绍一下你自己"
                        )
                    )
                )
            );

            return result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/run-test")
    @Operation(summary = "运行完整的MCP测试")
    public CompletableFuture<Map<String, Object>> runTest(@RequestBody Map<String, String> request) {
        String baseUrl = request.getOrDefault("baseUrl", "http://localhost:8080/mcp");
        String userMessage = request.getOrDefault("userMessage", "你好，请介绍一下你自己");

        return CompletableFuture.supplyAsync(() -> {
            try {
                String sessionId = UUID.randomUUID().toString();
                StringBuilder logBuilder = new StringBuilder();

                logBuilder.append("开始MCP测试...\n");
                logBuilder.append("Session ID: ").append(sessionId).append("\n\n");

                logBuilder.append("步骤1: 发送初始化请求\n");
                String initResponse = sendPostRequest(baseUrl + "/messages", sessionId,
                    createInitializeRequest());
                logBuilder.append("初始化响应: ").append(initResponse).append("\n\n");

                logBuilder.append("步骤2: 获取工具列表\n");
                String toolsResponse = sendPostRequest(baseUrl + "/messages", sessionId,
                    createToolsListRequest());
                logBuilder.append("工具列表响应: ").append(toolsResponse).append("\n\n");

                logBuilder.append("步骤3: 调用工具\n");
                String toolCallResponse = sendPostRequest(baseUrl + "/messages", sessionId,
                    createToolCallRequest(userMessage));
                logBuilder.append("工具调用响应: ").append(toolCallResponse).append("\n\n");

                return Map.of(
                    "success", true,
                    "sessionId", sessionId,
                    "log", logBuilder.toString()
                );
            } catch (Exception e) {
                return Map.of(
                    "success", false,
                    "error", e.getMessage()
                );
            }
        }, executorService);
    }

    private String createInitializeRequest() {
        return """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "TestClient",
                        "version": "1.0.0"
                    }
                }
            }
            """;
    }

    private String createToolsListRequest() {
        return """
            {
                "jsonrpc": "2.0",
                "id": "2",
                "method": "tools/list"
            }
            """;
    }

    private String createToolCallRequest(String userMessage) {
        return String.format("""
            {
                "jsonrpc": "2.0",
                "id": "3",
                "method": "tools/call",
                "params": {
                    "name": "composite_intelligent_agent",
                    "arguments": {
                        "userMessage": "%s"
                    }
                }
            }
            """, userMessage.replace("\"", "\\\""));
    }

    private String sendPostRequest(String urlString, String sessionId, String jsonBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Session-ID", sessionId);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
