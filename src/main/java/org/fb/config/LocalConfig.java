package org.fb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

/**
 * 本地开发环境配置
 */
@Configuration
public class LocalConfig {
    private static final Logger log = LoggerFactory.getLogger(LocalConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.local-mode", havingValue = "true", matchIfMissing = false)
    public WebMvcConfigurer localWebConfigurer() {
        return new WebMvcConfigurer() {
            public void addErrorHandlers(ExceptionHandlerExceptionResolver resolver) {
                log.info("Local mode enabled - adding error handlers");
            }
        };
    }
}