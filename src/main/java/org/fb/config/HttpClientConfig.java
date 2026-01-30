package org.fb.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class HttpClientConfig {

    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 300;
    private static final int MAX_CONNECTIONS = 50;
    private static final int MAX_PER_ROUTE = 20;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .readTimeout(Duration.ofSeconds(READ_TIMEOUT))
                .build();
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT * 1000);
        factory.setReadTimeout(READ_TIMEOUT * 1000);
        return factory;
    }
}
