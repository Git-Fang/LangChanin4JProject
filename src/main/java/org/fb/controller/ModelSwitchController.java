package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.ChatModelInfo;
import org.fb.bean.Result;
import org.fb.config.DynamicAiServiceFactory;
import org.fb.config.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理控制器
 * 提供动态模型切换相关接口
 */
@Tag(name = "模型管理", description = "动态模型切换相关接口")
@RestController
@RequestMapping("/api/model")
public class ModelSwitchController {
    private static final Logger log = LoggerFactory.getLogger(ModelSwitchController.class);

    @Autowired
    private ModelRegistry modelRegistry;

    @Autowired
    private DynamicAiServiceFactory aiServiceFactory;

    @Operation(summary = "获取可用模型列表", description = "返回所有已注册且可用的AI模型列表")
    @GetMapping("/list")
    public Result<List<ChatModelInfo>> getAvailableModels() {
        try {
            List<ChatModelInfo> models = modelRegistry.getAvailableModels();
            log.info("【API调用】获取可用模型列表，数量: {}, 模型列表: {}", 
                    models.size(), 
                    models.stream().map(ChatModelInfo::getModelId).collect(java.util.stream.Collectors.toList()));
            return Result.success(models);
        } catch (Exception e) {
            log.error("获取模型列表失败: {}", e.getMessage(), e);
            return Result.error("获取模型列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取所有模型列表", description = "返回所有已注册的AI模型列表（包括禁用的）")
    @GetMapping("/list/all")
    public Result<List<ChatModelInfo>> getAllModels() {
        try {
            List<ChatModelInfo> models = modelRegistry.getAllModels();
            log.info("获取所有模型列表成功，数量: {}", models.size());
            return Result.success(models);
        } catch (Exception e) {
            log.error("获取所有模型列表失败: {}", e.getMessage(), e);
            return Result.error("获取模型列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取当前默认模型", description = "返回当前默认使用的模型信息")
    @GetMapping("/current")
    public Result<ChatModelInfo> getCurrentModel(
            @Parameter(description = "模型ID，如果不传则返回默认模型")
            @RequestParam(value = "modelId", required = false) String modelId) {
        try {
            ChatModelInfo modelInfo;
            if (modelId != null && !modelId.isEmpty()) {
                modelInfo = modelRegistry.getModel(modelId);
            } else {
                String defaultModelId = modelRegistry.getDefaultModelId();
                modelInfo = modelRegistry.getModel(defaultModelId);
            }
            
            if (modelInfo == null) {
                return Result.error("未找到模型信息");
            }
            
            return Result.success(modelInfo);
        } catch (Exception e) {
            log.error("获取当前模型失败: {}", e.getMessage(), e);
            return Result.error("获取当前模型失败: " + e.getMessage());
        }
    }

    @Operation(summary = "切换指定模型", description = "切换到指定模型，会清除该模型的缓存")
    @PostMapping("/switch/{modelId}")
    public Result<String> switchModel(
            @Parameter(description = "模型ID") @PathVariable String modelId) {
        try {
            log.info("==================== 模型切换请求 ====================");
            log.info("【模型切换请求】目标模型ID: {}", modelId);
            
            ChatModelInfo modelInfo = modelRegistry.getModel(modelId);
            if (modelInfo == null) {
                log.warn("【模型切换失败】模型不存在 - {}", modelId);
                log.info("==================== 模型切换结束 ====================");
                return Result.error("模型不存在: " + modelId);
            }
            
            if (!modelInfo.isEnabled()) {
                log.warn("【模型切换失败】模型未启用 - {}, 启用状态: {}", modelId, modelInfo.isEnabled());
                log.info("==================== 模型切换结束 ====================");
                return Result.error("模型未启用: " + modelId);
            }
            
            // 清除该模型的缓存，强制重新创建
            aiServiceFactory.clearCache(modelId);
            
            log.info("【模型切换成功】模型ID: {}, 显示名: {}, 提供商: {}", 
                    modelId, modelInfo.getDisplayName(), modelInfo.getProvider());
            log.info("==================== 模型切换完成 ====================");
            return Result.success("模型切换成功: " + modelInfo.getDisplayName());
        } catch (Exception e) {
            log.error("【模型切换异常】modelId={}, error={}", modelId, e.getMessage(), e);
            log.info("==================== 模型切换异常结束 ====================");
            return Result.error("模型切换失败: " + e.getMessage());
        }
    }

    @Operation(summary = "启用/禁用模型", description = "动态启用或禁用指定模型")
    @PostMapping("/toggle/{modelId}")
    public Result<String> toggleModel(
            @Parameter(description = "模型ID") @PathVariable String modelId,
            @Parameter(description = "是否启用") @RequestParam boolean enabled) {
        try {
            modelRegistry.setModelEnabled(modelId, enabled);
            
            // 如果禁用模型，同时清除缓存
            if (!enabled) {
                aiServiceFactory.clearCache(modelId);
            }
            
            log.info("模型状态变更: {} -> {}", modelId, enabled ? "启用" : "禁用");
            return Result.success("模型" + (enabled ? "启用" : "禁用") + "成功");
        } catch (Exception e) {
            log.error("模型状态变更失败: modelId={}, error={}", modelId, e.getMessage(), e);
            return Result.error("模型状态变更失败: " + e.getMessage());
        }
    }

    @Operation(summary = "刷新模型配置", description = "重新加载LLM配置并刷新模型注册表")
    @PostMapping("/refresh")
    public Result<String> refreshModels() {
        try {
            aiServiceFactory.refreshAllModels();
            log.info("模型配置已刷新");
            return Result.success("模型配置已刷新");
        } catch (Exception e) {
            log.error("模型配置刷新失败: {}", e.getMessage(), e);
            return Result.error("模型配置刷新失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取模型缓存状态", description = "返回当前缓存的AiService数量")
    @GetMapping("/cache/status")
    public Result<java.util.Map<String, Object>> getCacheStatus() {
        try {
            java.util.Map<String, Object> status = new java.util.HashMap<>();
            status.put("cacheSize", aiServiceFactory.getCacheSize());
            status.put("availableModels", modelRegistry.getAvailableModels().size());
            status.put("defaultModel", modelRegistry.getDefaultModelId());
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取缓存状态失败: {}", e.getMessage(), e);
            return Result.error("获取缓存状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "清除指定模型缓存", description = "清除指定模型的AiService缓存")
    @DeleteMapping("/cache/{modelId}")
    public Result<String> clearCache(
            @Parameter(description = "模型ID") @PathVariable String modelId) {
        try {
            aiServiceFactory.clearCache(modelId);
            log.info("模型缓存已清除: {}", modelId);
            return Result.success("模型缓存已清除: " + modelId);
        } catch (Exception e) {
            log.error("清除缓存失败: modelId={}, error={}", modelId, e.getMessage(), e);
            return Result.error("清除缓存失败: " + e.getMessage());
        }
    }

    @Operation(summary = "清除所有模型缓存", description = "清除所有模型的AiService缓存")
    @DeleteMapping("/cache")
    public Result<String> clearAllCache() {
        try {
            aiServiceFactory.clearAllCache();
            log.info("所有模型缓存已清除");
            return Result.success("所有模型缓存已清除");
        } catch (Exception e) {
            log.error("清除所有缓存失败: {}", e.getMessage(), e);
            return Result.error("清除所有缓存失败: " + e.getMessage());
        }
    }
}
