package org.fb.config;

import jakarta.annotation.PostConstruct;
import org.fb.service.ModelAwareChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigRefreshListener implements ApplicationListener<RefreshScopeRefreshedEvent> {
    private static final Logger log = LoggerFactory.getLogger(ConfigRefreshListener.class);

    @Autowired
    private LLMConfig llmConfig;

    @Autowired
    private ModelAwareChatService modelAwareChatService;

    @Value("${ai.deepSeek.model:unknown}")
    private String currentDeepSeekModel;

    @PostConstruct
    public void init() {
        log.info("ConfigRefreshListener initialized, will refresh models on config change");
        log.info("Initial DeepSeek model: {}", currentDeepSeekModel);
    }

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        log.info("Received RefreshScopeRefreshedEvent, refreshing all chat models");
        log.info("Current DeepSeek model from @Value: {}", currentDeepSeekModel);
        try {
            llmConfig.refreshAllModels();
            modelAwareChatService.refreshAllModels();
            log.info("All chat models and AI services refreshed successfully after config change");
            log.info("New DeepSeek model should be: {}", currentDeepSeekModel);
        } catch (Exception e) {
            log.error("Failed to refresh chat models after config change", e);
        }
    }
}
