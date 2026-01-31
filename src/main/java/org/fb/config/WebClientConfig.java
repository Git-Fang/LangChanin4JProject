package org.fb.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 600;
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    private static final int MAX_CONNECTIONS = 100;
    private static final int MAX_IDLE_CONNECTIONS = 20;
    private static final long KEEP_ALIVE_DURATION = Duration.ofSeconds(30).toMillis();

    @Bean
    public WebClient webClient() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-chat-pool")
                .maxConnections(MAX_CONNECTIONS)
                .maxIdleTime(Duration.ofSeconds(30))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .pendingAcquireMaxCount(1000)
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT * 1000)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT, TimeUnit.SECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(READ_TIMEOUT, TimeUnit.SECONDS))
                )
                .option(ChannelOption.SO_KEEPALIVE, true);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
