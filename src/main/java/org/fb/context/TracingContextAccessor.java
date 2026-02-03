package org.fb.context;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 追踪上下文访问器
 * 实现ThreadLocalAccessor接口，用于在Reactor响应式流和ForkJoinPool中传递MDC中的trace信息
 * 
 * Brave/Micrometer将trace信息存储在MDC中：
 * - traceId: 追踪ID
 * - spanId: Span ID
 * - parentSpanId: 父Span ID (可选)
 * - sampled: 采样标识 (可选)
 */
public class TracingContextAccessor implements ThreadLocalAccessor<Map<String, String>> {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String PARENT_SPAN_ID_KEY = "parentSpanId";
    public static final String SAMPLED_KEY = "sampled";

    @Override
    public Object key() {
        return TracingContextAccessor.class;
    }

    @Override
    public Map<String, String> getValue() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 获取MDC副本（公开方法）
     * @return MDC内容的副本
     */
    public Map<String, String> getMdcCopy() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    public void setValue(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            MDC.clear();
        } else {
            // 过滤掉null值，只保留有效的trace信息
            Map<String, String> filteredValues = value.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            if (filteredValues.isEmpty()) {
                MDC.clear();
            } else {
                filteredValues.forEach(MDC::put);
            }
        }
    }

    @Override
    public void setValue() {
        MDC.clear();
    }

    /**
     * 获取当前traceId
     * @return traceId字符串，如果不存在则返回null
     */
    public static String getCurrentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 获取当前spanId
     * @return spanId字符串，如果不存在则返回null
     */
    public static String getCurrentSpanId() {
        return MDC.get(SPAN_ID_KEY);
    }

    /**
     * 检查当前是否在追踪上下文中
     * @return 如果traceId存在且不为空则返回true
     */
    public static boolean isInTraceContext() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null && !traceId.isEmpty();
    }
}
