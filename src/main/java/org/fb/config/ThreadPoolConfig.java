package org.fb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置
 */
@Configuration
public class ThreadPoolConfig {

    private static final int THREAD_POOL_SIZE = 10;
    private static final int THREAD_POOL_QUEUE_SIZE = 100;

    @Bean(name = "mdcExecutorService")
    public ThreadPoolExecutor mdcExecutorService() {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_SIZE);
        return new MDCThreadPoolExecutor(
                THREAD_POOL_SIZE,
                THREAD_POOL_SIZE * 2,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
