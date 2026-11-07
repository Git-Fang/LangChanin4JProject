package org.fb.trace;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LangChain4j 聊天模型监听器：把每次模型调用转换为 Langfuse 的 trace + generation。
 * <p>
 * 一次模型调用 = 一条 trace（含 input/output/session）+ 一条 GENERATION observation
 * （含 model、token 用量、耗时、finishReason）。异常调用会以 ERROR 级别上报。
 * 所有异常都在内部吞掉，绝不影响业务请求。
 */
public class LangfuseChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LangfuseChatModelListener.class);

    private static final String ATTR_START = "langfuse.startNanos";
    private static final String ATTR_INPUT = "langfuse.input";
    private static final String ATTR_MODEL = "langfuse.model";

    private static final String TRACE_NAME = envOr("LANGFUSE_TRACE_NAME", "lc4j-chat");

    @Override
    public void onRequest(ChatModelRequestContext context) {
        try {
            Map<Object, Object> attrs = context.attributes();
            attrs.put(ATTR_START, System.nanoTime());
            attrs.put(ATTR_INPUT, toMessageList(context.chatRequest()));
            attrs.put(ATTR_MODEL, modelNameOf(context.chatRequest(), context.modelProvider()));
        } catch (Exception e) {
            log.debug("【Langfuse】onRequest 处理失败: {}", e.toString());
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        try {
            if (!LangfuseTracer.enabled()) {
                return;
            }
            Map<Object, Object> attrs = context.attributes();
            long startNanos = attrs.get(ATTR_START) instanceof Long l ? l : System.nanoTime();
            long latencyMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> input = attrs.get(ATTR_INPUT) instanceof List
                    ? (List<Map<String, String>>) attrs.get(ATTR_INPUT)
                    : toMessageList(context.chatRequest());

            ChatResponse response = context.chatResponse();
            String model = firstNonBlank(
                    response.modelName(),
                    response.metadata() == null ? null : response.metadata().modelName(),
                    attrs.get(ATTR_MODEL) == null ? null : String.valueOf(attrs.get(ATTR_MODEL)),
                    context.modelProvider() == null ? null : context.modelProvider().name());

            List<Map<String, String>> output = new ArrayList<>();
            if (response.aiMessage() != null) {
                output.add(message("assistant", textOf(response.aiMessage())));
            }

            TokenUsage usage = response.tokenUsage();
            Instant end = Instant.now();
            Instant start = end.minusMillis(latencyMs);

            Map<String, Object> meta = baseMetadata(model, latencyMs);
            meta.put("provider", context.modelProvider() == null ? "" : context.modelProvider().name());
            if (response.finishReason() != null) {
                meta.put("finishReason", response.finishReason().name());
            }
            if (response.id() != null) {
                meta.put("responseId", response.id());
            }

            String traceId = traceIdFromMdc();

            Map<String, Object> traceBody = new LinkedHashMap<>();
            traceBody.put("id", traceId);
            traceBody.put("name", TRACE_NAME);
            traceBody.put("timestamp", LangfuseTracer.iso(start));
            traceBody.put("input", input);
            traceBody.put("output", output);
            traceBody.put("metadata", meta);
            traceBody.put("tags", List.of("langchain4j", "auto"));
            applySessionContext(traceBody);

            Map<String, Object> generationBody = new LinkedHashMap<>();
            generationBody.put("id", newId());
            generationBody.put("traceId", traceId);
            generationBody.put("name", "chat");
            generationBody.put("model", model);
            generationBody.put("startTime", LangfuseTracer.iso(start));
            generationBody.put("endTime", LangfuseTracer.iso(end));
            generationBody.put("input", input);
            generationBody.put("output", output);
            generationBody.put("metadata", meta);
            generationBody.put("level", "DEFAULT");
            if (usage != null) {
                generationBody.put("usage", usageMap(usage));
            }

            LangfuseTracer.sendAsync(List.of(
                    event("trace-create", traceBody),
                    event("generation-create", generationBody)));
        } catch (Exception e) {
            log.debug("【Langfuse】onResponse 处理失败: {}", e.toString());
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        try {
            if (!LangfuseTracer.enabled()) {
                return;
            }
            Map<Object, Object> attrs = context.attributes();
            long startNanos = attrs.get(ATTR_START) instanceof Long l ? l : System.nanoTime();
            long latencyMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> input = attrs.get(ATTR_INPUT) instanceof List
                    ? (List<Map<String, String>>) attrs.get(ATTR_INPUT)
                    : toMessageList(context.chatRequest());

            String model = firstNonBlank(
                    attrs.get(ATTR_MODEL) == null ? null : String.valueOf(attrs.get(ATTR_MODEL)),
                    context.modelProvider() == null ? null : context.modelProvider().name());
            Instant end = Instant.now();
            Instant start = end.minusMillis(latencyMs);

            Map<String, Object> meta = baseMetadata(model, latencyMs);
            meta.put("error", context.error() == null ? "unknown" : context.error().toString());

            String traceId = traceIdFromMdc();
            Map<String, Object> traceBody = new LinkedHashMap<>();
            traceBody.put("id", traceId);
            traceBody.put("name", TRACE_NAME + "-error");
            traceBody.put("timestamp", LangfuseTracer.iso(start));
            traceBody.put("input", input);
            traceBody.put("output", List.of());
            traceBody.put("metadata", meta);
            traceBody.put("tags", List.of("langchain4j", "auto", "error"));
            applySessionContext(traceBody);

            Map<String, Object> genBody = new LinkedHashMap<>();
            genBody.put("id", newId());
            genBody.put("traceId", traceId);
            genBody.put("name", "chat");
            genBody.put("model", model);
            genBody.put("startTime", LangfuseTracer.iso(start));
            genBody.put("endTime", LangfuseTracer.iso(end));
            genBody.put("input", input);
            genBody.put("output", List.of());
            genBody.put("metadata", meta);
            genBody.put("level", "ERROR");
            genBody.put("statusMessage", context.error() == null ? "unknown" : context.error().toString());

            LangfuseTracer.sendAsync(List.of(
                    event("trace-create", traceBody),
                    event("generation-create", genBody)));
        } catch (Exception e) {
            log.debug("【Langfuse】onError 处理失败: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------

/** 同一次 HTTP 请求内的多次模型调用复用同一个 traceId（来自 MDC），便于按请求聚合。 */
    private static String traceIdFromMdc() {
        String mdc = MDC.get("traceId");
        return (mdc == null || mdc.isBlank()) ? newId() : mdc;
    }

    private static Map<String, Object> event(String type, Map<String, Object> body) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", newId());
        e.put("type", type);
        e.put("timestamp", LangfuseTracer.now());
        e.put("body", body);
        return e;
    }

    private static Map<String, Object> baseMetadata(String model, long latencyMs) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("model", model == null ? "" : model);
        meta.put("latencyMs", latencyMs);
        meta.put("source", "ragtranslation-lc4j");
        meta.put("environment", LangfuseTracer.environment());
        if (!LangfuseTracer.release().isEmpty()) {
            meta.put("release", LangfuseTracer.release());
        }
        String mdcTrace = MDC.get("traceId");
        if (mdcTrace != null && !mdcTrace.isEmpty()) {
            meta.put("logTraceId", mdcTrace);
        }
        return meta;
    }

    private static void applySessionContext(Map<String, Object> traceBody) {
        String session = MDC.get("sessionId");
        if (session == null || session.isEmpty()) {
            session = MDC.get("traceId");
        }
        if (session != null && !session.isEmpty()) {
            traceBody.put("sessionId", session);
        }
        String user = MDC.get("userId");
        if (user != null && !user.isEmpty()) {
            traceBody.put("userId", user);
        }
    }

    private static Map<String, Object> usageMap(TokenUsage usage) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("input", usage.inputTokenCount() == null ? 0 : usage.inputTokenCount());
        u.put("output", usage.outputTokenCount() == null ? 0 : usage.outputTokenCount());
        u.put("total", usage.totalTokenCount() == null ? 0 : usage.totalTokenCount());
        u.put("unit", "TOKENS");
        return u;
    }

    private static List<Map<String, String>> toMessageList(ChatRequest request) {
        List<Map<String, String>> result = new ArrayList<>();
        if (request == null || request.messages() == null) {
            return result;
        }
        for (ChatMessage msg : request.messages()) {
            result.add(message(roleOf(msg), textOf(msg)));
        }
        return result;
    }

    private static String roleOf(ChatMessage msg) {
        if (msg instanceof SystemMessage) {
            return "system";
        }
        if (msg instanceof UserMessage) {
            return "user";
        }
        if (msg instanceof AiMessage) {
            return "assistant";
        }
        if (msg instanceof ToolExecutionResultMessage) {
            return "tool";
        }
        return "custom";
    }

    private static String textOf(ChatMessage msg) {
        try {
            if (msg instanceof SystemMessage sm) {
                return nullSafe(sm.text());
            }
            if (msg instanceof UserMessage um) {
                if (um.hasSingleText()) {
                    return nullSafe(um.singleText());
                }
                return um.contents() == null ? "" : um.contents().toString();
            }
            if (msg instanceof AiMessage am) {
                if (am.text() != null) {
                    return am.text();
                }
                return am.toolExecutionRequests() == null ? "" : am.toolExecutionRequests().toString();
            }
            if (msg instanceof ToolExecutionResultMessage tm) {
                return nullSafe(tm.text());
            }
            if (msg instanceof CustomMessage) {
                return nullSafe(msg.toString());
            }
        } catch (Exception ignored) {
            // 兜底：内容解析失败不中断业务
        }
        return "";
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private static String modelNameOf(ChatRequest request, dev.langchain4j.model.ModelProvider provider) {
        try {
            if (request != null && request.parameters() != null && request.parameters().modelName() != null) {
                return request.parameters().modelName();
            }
        } catch (Exception ignored) {
        }
        return provider == null ? "" : provider.name();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "unknown";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String envOr(String name, String def) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            v = System.getProperty(name);
        }
        return (v == null || v.isBlank()) ? def : v;
    }
}