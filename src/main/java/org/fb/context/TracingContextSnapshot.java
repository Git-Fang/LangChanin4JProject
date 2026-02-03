package org.fb.context;

import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 追踪上下文快照工具类
 * 用于在异步操作（ForkJoinPool、线程池等）中正确传递和恢复MDC中的trace信息
 * 
 * 使用示例：
 * <pre>
 * // 在主线程中捕获trace上下文
 * Map<String, String> context = TracingContextSnapshot.capture();
 * 
 * // 使用contextWrite传递到响应式链
 * return TracingContextSnapshot.contextWrite(source, context)
 *     .flatMap(...)  // 在ForkJoinPool线程中，MDC会自动恢复
 * 
 * // 或者使用executeWithContext自动管理
 * return TracingContextSnapshot.executeWithContext(() -> {
 *     // 这里MDC会自动恢复到捕获时的状态
 *     yourAsyncOperation();
 * });
 * </pre>
 */
public final class TracingContextSnapshot {

    private static final TracingContextAccessor TRACING_ACCESSOR = new TracingContextAccessor();

    // Reactor Context的key，用于存储trace上下文
    private static final String REACTOR_CONTEXT_KEY = "TRACING_CONTEXT";

    private TracingContextSnapshot() {
        // 工具类，禁止实例化
    }

    /**
     * 捕获当前线程的追踪上下文（MDC中的trace信息）
     * @return 包含traceId、spanId等信息的Map
     */
    public static Map<String, String> capture() {
        return TRACING_ACCESSOR.getMdcCopy();
    }

    /**
     * 恢复追踪上下文到MDC
     * @param contextMap 从capture()捕获的上下文Map
     */
    public static void restore(Map<String, String> contextMap) {
        TRACING_ACCESSOR.setValue(contextMap);
    }

    /**
     * 将trace上下文写入Reactor Context并返回包装后的Flux
     * @param source 原始Flux
     * @param traceContext 捕获的trace上下文
     * @param <T> 元素类型
     * @return 包装后的Flux，订阅时会自动恢复trace上下文
     */
    public static <T> Flux<T> contextWrite(Flux<T> source, Map<String, String> traceContext) {
        if (traceContext == null || traceContext.isEmpty()) {
            return source;
        }
        return Flux.defer(() -> {
            // 在订阅时（ForkJoinPool线程中）恢复MDC
            Map<String, String> previousContext = TRACING_ACCESSOR.getMdcCopy();
            TRACING_ACCESSOR.setValue(traceContext);
            
            return source
                    .doOnCancel(() -> TRACING_ACCESSOR.setValue(previousContext))
                    .doOnComplete(() -> TRACING_ACCESSOR.setValue(previousContext))
                    .doOnError(e -> TRACING_ACCESSOR.setValue(previousContext));
        });
    }

    /**
     * 将trace上下文写入Reactor Context并返回包装后的Mono
     * @param source 原始Mono
     * @param traceContext 捕获的trace上下文
     * @param <T> 元素类型
     * @return 包装后的Mono，订阅时会自动恢复trace上下文
     */
    public static <T> Mono<T> contextWrite(Mono<T> source, Map<String, String> traceContext) {
        if (traceContext == null || traceContext.isEmpty()) {
            return source;
        }
        return Mono.defer(() -> {
            // 在订阅时（ForkJoinPool线程中）恢复MDC
            Map<String, String> previousContext = TRACING_ACCESSOR.getMdcCopy();
            TRACING_ACCESSOR.setValue(traceContext);
            
            return source
                    .doOnCancel(() -> TRACING_ACCESSOR.setValue(previousContext))
                    .doOnTerminate(() -> TRACING_ACCESSOR.setValue(previousContext));
        });
    }

    /**
     * 在代码块中执行任务，自动保存和恢复trace上下文
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果
     */
    public static <T> T executeWithContext(Supplier<T> task) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        Map<String, String> currentContext = TRACING_ACCESSOR.getMdcCopy();
        try {
            TRACING_ACCESSOR.setValue(currentContext);
            return task.get();
        } finally {
            TRACING_ACCESSOR.setValue(previousContext);
        }
    }

    /**
     * 在代码块中执行任务，自动保存和恢复trace上下文
     * @param task 要执行的任务
     */
    public static void executeWithContext(Runnable task) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        Map<String, String> currentContext = TRACING_ACCESSOR.getMdcCopy();
        try {
            TRACING_ACCESSOR.setValue(currentContext);
            task.run();
        } finally {
            TRACING_ACCESSOR.setValue(previousContext);
        }
    }

    /**
     * 创建一个包装器，用于包装Supplier操作
     * @param supplier 原始Supplier
     * @param <T> 返回类型
     * @return 包装后的Supplier，执行时会自动恢复trace上下文
     */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Map<String, String> contextMap = TRACING_ACCESSOR.getMdcCopy();
        return () -> {
            Map<String, String> previousContext = TRACING_ACCESSOR.getMdcCopy();
            try {
                TRACING_ACCESSOR.setValue(contextMap);
                return supplier.get();
            } finally {
                TRACING_ACCESSOR.setValue(previousContext);
            }
        };
    }

    /**
     * 创建一个包装器，用于包装Runnable操作
     * @param runnable 原始Runnable
     * @return 包装后的Runnable，执行时会自动恢复trace上下文
     */
    public static Runnable wrapRunnable(Runnable runnable) {
        Map<String, String> contextMap = TRACING_ACCESSOR.getMdcCopy();
        return () -> {
            Map<String, String> previousContext = TRACING_ACCESSOR.getMdcCopy();
            try {
                TRACING_ACCESSOR.setValue(contextMap);
                runnable.run();
            } finally {
                TRACING_ACCESSOR.setValue(previousContext);
            }
        };
    }
}
