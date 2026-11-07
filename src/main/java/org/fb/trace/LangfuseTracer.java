package org.fb.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Langfuse 上报器。
 * <p>
 * 通过环境变量配置：
 * <ul>
 *   <li>LANGFUSE_ENABLED       默认 true</li>
 *   <li>LANGFUSE_HOST          默认 http://host.docker.internal:3010</li>
 *   <li>LANGFUSE_PUBLIC_KEY    必填</li>
 *   <li>LANGFUSE_SECRET_KEY    必填</li>
 *   <li>LANGFUSE_ENVIRONMENT   默认 production</li>
 *   <li>LANGFUSE_RELEASE       可选，版本号</li>
 *   <li>LANGFUSE_FLUSH_INTERVAL_MS  预留</li>
 * </ul>
 * 使用 Langfuse 的 batch ingestion 接口，异步发送，不阻塞业务线程。
 */
public final class LangfuseTracer {

    private static final Logger log = LoggerFactory.getLogger(LangfuseTracer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final boolean ENABLED;
    private static final String HOST;
    private static final String AUTH_HEADER;
    private static final String ENVIRONMENT;
    private static final String RELEASE;
    private static final List<ChatModelListener> LISTENERS;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final ExecutorService SENDER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "langfuse-sender");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    static {
        String enabledEnv = env("LANGFUSE_ENABLED", "true");
        String rawHost = env("LANGFUSE_HOST", "http://host.docker.internal:3010");
        String publicKey = env("LANGFUSE_PUBLIC_KEY", "");
        String secretKey = env("LANGFUSE_SECRET_KEY", "");
        ENVIRONMENT = env("LANGFUSE_ENVIRONMENT", "production");
        RELEASE = env("LANGFUSE_RELEASE", "");

        while (rawHost.endsWith("/")) {
            rawHost = rawHost.substring(0, rawHost.length() - 1);
        }
        HOST = rawHost;

        boolean ok = Boolean.parseBoolean(enabledEnv) && !publicKey.isEmpty() && !secretKey.isEmpty();
        ENABLED = ok;
        AUTH_HEADER = ok
                ? "Basic " + Base64.getEncoder().encodeToString(
                        (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8))
                : null;

        LISTENERS = ok
                ? Collections.unmodifiableList(List.of(new LangfuseChatModelListener()))
                : Collections.emptyList();

        if (ok) {
            log.info("【Langfuse】已启用，host={}, environment={}", HOST, ENVIRONMENT);
        } else {
            log.info("【Langfuse】未启用（LANGFUSE_ENABLED={}，且需配置 PUBLIC/SECRET KEY）", enabledEnv);
        }
    }

    private LangfuseTracer() {
    }

    /** 供各 ChatModel builder 使用：未启用时返回空列表，builder 调用无副作用。 */
    public static List<ChatModelListener> listeners() {
        return LISTENERS;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static String host() {
        return HOST;
    }

    public static String environment() {
        return ENVIRONMENT;
    }

    public static String release() {
        return RELEASE;
    }

    public static String now() {
        return TS.format(Instant.now());
    }

    public static String iso(Instant instant) {
        return TS.format(instant);
    }

    /** 异步发送一批事件，失败仅记录日志。 */
    public static void sendAsync(List<Map<String, Object>> events) {
        if (!ENABLED || events == null || events.isEmpty()) {
            return;
        }
        SENDER.submit(() -> send(events));
    }

    private static void send(List<Map<String, Object>> events) {
        try {
            List<Map<String, Object>> batch = new ArrayList<>(events);
            String body = MAPPER.writeValueAsString(Map.of("batch", batch));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HOST + "/api/public/ingestion"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", AUTH_HEADER)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                if (WARNED.compareAndSet(false, true)) {
                    log.warn("【Langfuse】上报失败 status={}, body={}", resp.statusCode(),
                            resp.body() == null ? "" : resp.body().substring(0, Math.min(300, resp.body().length())));
                }
            }
        } catch (Exception e) {
            if (WARNED.compareAndSet(false, true)) {
                log.warn("【Langfuse】上报异常: {}", e.toString());
            }
        }
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(name);
        }
        return (v == null || v.isEmpty()) ? def : v;
    }
}