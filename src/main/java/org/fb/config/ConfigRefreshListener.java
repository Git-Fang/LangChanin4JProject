package org.fb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigRefreshListener implements ApplicationListener<RefreshScopeRefreshedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    @Autowired
    private LLMConfig llmConfig;

    @PostConstruct
    public void init() {
        log.info("ConfigRefreshListener initialized, will refresh models on config change");
    }

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        log.info("Received RefreshScopeRefreshedEvent, refreshing all chat models");
        try {
            llmConfig.refreshAllModels();
            log.info("All chat models refreshed successfully after config change");
        } catch (Exception e) {
            log.error("Failed to refresh chat models after config change", e);
        }
    }
}
