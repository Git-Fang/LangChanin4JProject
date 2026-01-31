package org.fb.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
public class HttpClientConfig {
    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 300;
    private static final int MAX_CONNECTIONS = 100;
    private static final int MAX_PER_ROUTE = 50;
    private static final int VALIDATE_AFTER_INACTIVITY = 5000;

    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        connectionManager = createConnectionManager();
        httpClient = createHttpClient(connectionManager);
        log.info("HTTP连接池初始化完成, 最大连接数: {}, 每路由最大连接: {}",
                MAX_CONNECTIONS, MAX_PER_ROUTE);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
            if (connectionManager != null) {
                connectionManager.close();
            }
            log.info("HTTP连接池已关闭");
        } catch (Exception e) {
            log.error("关闭HTTP连接池时发生错误", e);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void idleConnectionEvictionTask() {
        if (connectionManager != null) {
            int totalConnections = connectionManager.getTotalStats().getLeased();
            int availableConnections = connectionManager.getTotalStats().getAvailable();
            if (totalConnections > 0 || availableConnections > 0) {
                log.debug("HTTP连接池状态 - 已分配: {}, 可用: {}", totalConnections, availableConnections);
            }
        }
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .readTimeout(Duration.ofSeconds(READ_TIMEOUT))
                .build();
    }

    @Bean
    public PoolingHttpClientConnectionManager poolingConnectionManager() {
        return createConnectionManager();
    }

    private PoolingHttpClientConnectionManager createConnectionManager() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(MAX_CONNECTIONS);
        manager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        manager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY);
        return manager;
    }

    @Bean
    public CloseableHttpClient httpClient() {
        return createHttpClient(connectionManager);
    }

    private CloseableHttpClient createHttpClient(PoolingHttpClientConnectionManager manager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(READ_TIMEOUT)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                .build();

        return HttpClientBuilder.create()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT * 1000);
        factory.setReadTimeout(READ_TIMEOUT * 1000);
        return factory;
    }
}
