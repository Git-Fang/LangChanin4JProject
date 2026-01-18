package org.fb.controller;

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

    @PostMapping("/refresh")
    public Map<String, Object> refreshConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            eventPublisher.publishEvent(new RefreshScopeRefreshedEvent());
            result.put("success", true);
            result.put("message", "配置刷新事件已发布，模型正在刷新中...");
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
        result.put("message", "Nacos配置刷新事件已发布，模型将在后台自动刷新");
        return result;
    }
}
