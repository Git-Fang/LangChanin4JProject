package org.fb.controller;

import org.fb.config.LLMConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/config")
@RefreshScope
public class ConfigRefreshController {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LLMConfig llmConfig;

    @PostMapping("/refresh")
    public Map<String, Object> refreshConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            eventPublisher.publishEvent(new RefreshScopeRefreshedEvent());
            llmConfig.refreshAllModels();
            result.put("success", true);
            result.put("message", "配置已刷新，动态配置已更新");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "配置刷新失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/refresh-nacos")
    public Map<String, Object> refreshNacosConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Nacos配置已更新，请在5秒内调用 /config/refresh 刷新本地配置");
        result.put("nextStep", "POST /config/refresh");
        return result;
    }
}
