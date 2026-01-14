package org.fb.config;

import org.fb.constant.BusinessConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "mdcExecutorService")
    public ThreadPoolExecutor mdcExecutorService() {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(BusinessConstant.THREAD_POOL_QUEUE_SIZE);
        return new MDCThreadPoolExecutor(
                BusinessConstant.THREAD_POOL_SIZE,
                BusinessConstant.THREAD_POOL_SIZE * 2,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
