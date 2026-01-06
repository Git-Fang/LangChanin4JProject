package org.fb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fb.bean.mcpbean.McpMessage;
import org.fb.bean.mcpbean.McpToolCallParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpSseService {
    private static final Logger logger = LoggerFactory.getLogger(McpSseService.class);

    private final List<McpTool> tools;
    private final ObjectMapper objectMapper;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> initializedSessions = new ConcurrentHashMap<>();

    @Autowired
    public McpSseService(List<McpTool> tools, ObjectMapper objectMapper) {
        this.tools = tools;
        this.objectMapper = objectMapper;
    }

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> {
            emitters.remove(sessionId);
            initializedSessions.remove(sessionId);
            logger.info("SSE会话已关闭: {}", sessionId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(sessionId);
            initializedSessions.remove(sessionId);
            logger.info("SSE会话超时: {}", sessionId);
        });

        emitter.onError((ex) -> {
            emitters.remove(sessionId);
            initializedSessions.remove(sessionId);
            logger.error("SSE会话错误: {}", sessionId, ex);
        });

        emitters.put(sessionId, emitter);
        logger.info("创建新的SSE会话: {}", sessionId);

        try {
            emitter.send(SseEmitter.event().comment("SSE连接已建立").reconnectTime(1000));
            logger.info("SSE初始消息已发送: sessionId={}", sessionId);
        } catch (IOException e) {
            logger.error("发送SSE初始消息失败: sessionId={}", sessionId, e);
        }

        return emitter;
    }

    public void sendMessage(String sessionId, McpMessage message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                logger.info("发送SSE消息: sessionId={}, json={}", sessionId, jsonMessage);
                emitter.send(SseEmitter.event().data(jsonMessage));
            } catch (IOException e) {
                logger.error("发送SSE消息失败: sessionId={}, message={}", sessionId, message, e);
            }
        } else {
            logger.warn("SSE会话不存在: sessionId={}", sessionId);
        }
    }

    public void complete(String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            emitter.complete();
            emitters.remove(sessionId);
            initializedSessions.remove(sessionId);
        }
    }

    public Mono<Void> handleMessage(String sessionId, McpMessage message) {
        String method = message.getMethod();
        String requestId = message.getId();
        Object params = message.getParams();

        logger.info("处理MCP消息: sessionId={}, method={}, requestId={}", sessionId, method, requestId);

        return switch (method) {
            case "initialize" -> handleInitialize(sessionId, requestId, params);
            case "initialized" -> handleInitialized(sessionId, requestId);
            case "shutdown" -> handleShutdown(sessionId, requestId);
            case "tools/list" -> handleToolsList(sessionId, requestId);
            case "tools/call" -> handleToolCall(sessionId, requestId, params);
            default -> Mono.fromRunnable(() -> {
                McpMessage error = McpMessage.error(requestId, -32601, "方法未找到: " + method);
                sendMessage(sessionId, error);
            });
        };
    }

    private Mono<Void> handleInitialize(String sessionId, String requestId, Object params) {
        logger.info("处理初始化请求: sessionId={}", sessionId);

        Map<String, Object> serverCapabilities = Map.of(
            "tools", Map.of(),
            "resources", Map.of(),
            "prompts", Map.of()
        );

        Map<String, Object> serverInfo = Map.of(
            "name", "RAGTranslation-MCP-Server",
            "version", "1.0.0"
        );

        Map<String, Object> result = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", serverCapabilities,
            "serverInfo", serverInfo
        );

        McpMessage response = McpMessage.response(requestId, result);
        sendMessage(sessionId, response);

        initializedSessions.put(sessionId, true);
        return Mono.empty();
    }

    private Mono<Void> handleInitialized(String sessionId, String requestId) {
        logger.info("处理初始化完成通知: sessionId={}", sessionId);
        initializedSessions.put(sessionId, true);
        return Mono.empty();
    }

    private Mono<Void> handleShutdown(String sessionId, String requestId) {
        logger.info("处理关闭请求: sessionId={}", sessionId);
        initializedSessions.remove(sessionId);
        McpMessage response = McpMessage.response(requestId, null);
        sendMessage(sessionId, response);
        return Mono.empty();
    }

    private Mono<Void> handleToolsList(String sessionId, String requestId) {
        logger.info("处理工具列表请求: sessionId={}, requestId={}", sessionId, requestId);
        logger.info("当前注册的工具数量: {}", tools.size());

        tools.forEach(tool -> {
            logger.info("工具详情: name={}, description={}", tool.getName(), tool.getDescription());
        });

        List<Map<String, Object>> toolList = tools.stream()
            .map(tool -> {
                logger.info("处理工具: name={}", tool.getName());
                return Map.of(
                    "name", tool.getName(),
                    "description", tool.getDescription(),
                    "inputSchema", tool.getInputSchema()
                );
            })
            .toList();

        logger.info("生成的工具列表: {}", toolList);

        Map<String, Object> result = Map.of("tools", toolList);
        McpMessage response = McpMessage.response(requestId, result);
        
        logger.info("发送工具列表响应: sessionId={}, response={}", sessionId, response);
        sendMessage(sessionId, response);

        return Mono.empty();
    }

    private Mono<Void> handleToolCall(String sessionId, String requestId, Object params) {
        logger.info("处理工具调用请求: sessionId={}, requestId={}", sessionId, requestId);

        if (!(params instanceof Map)) {
            McpMessage error = McpMessage.error(requestId, -32602, "无效的参数格式");
            sendMessage(sessionId, error);
            return Mono.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> paramMap = (Map<String, Object>) params;
        String toolName = (String) paramMap.get("name");
        Object arguments = paramMap.get("arguments");

        return tools.stream()
            .filter(tool -> tool.getName().equals(toolName))
            .findFirst()
            .map(tool -> tool.execute(arguments)
                .doOnSuccess(result -> {
                    Map<String, Object> callResult = Map.of(
                        "content", List.of(Map.of(
                            "type", "text",
                            "text", result != null ? result.toString() : ""
                        ))
                    );
                    McpMessage response = McpMessage.response(requestId, callResult);
                    sendMessage(sessionId, response);
                })
                .doOnError(error -> {
                    logger.error("工具执行失败: toolName={}, error={}", toolName, error.getMessage(), error);
                    McpMessage errorResponse = McpMessage.error(requestId, -32603, "工具执行失败: " + error.getMessage());
                    sendMessage(sessionId, errorResponse);
                })
                .then())
            .orElseGet(() -> {
                McpMessage error = McpMessage.error(requestId, -32602, "工具未找到: " + toolName);
                sendMessage(sessionId, error);
                return Mono.empty();
            });
    }
}
