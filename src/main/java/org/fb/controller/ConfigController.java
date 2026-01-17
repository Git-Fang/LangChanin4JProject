package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/config")
@Tag(name = "配置管理")
public class ConfigController {

    @Value("${app.test.value:test}")
    private String testValue;

    @Value("${app.dynamic.enabled:true}")
    private boolean dynamicEnabled;

    @Value("${app.dynamic.refresh-interval:30}")
    private int refreshInterval;

    @GetMapping("/test")
    @Operation(summary = "测试配置")
    public Map<String, Object> testConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("testValue", testValue);
        result.put("dynamicEnabled", dynamicEnabled);
        result.put("refreshInterval", refreshInterval);
        result.put("timestamp", System.currentTimeMillis());
        result.put("source", "local");
        return result;
    }

    @GetMapping("/status")
    @Operation(summary = "获取配置状态")
    public Map<String, Object> getConfigStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("nacosEnabled", false);
        result.put("message", "Nacos配置中心未启用，使用本地配置");
        return result;
    }
}
