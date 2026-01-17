package org.fb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.Executor;

@Configuration
@ConditionalOnClass(name = {"com.alibaba.nacos.api.config.ConfigService",
                             "com.alibaba.nacos.api.NacosFactory"})
public class NacosConfig {

    private static final Logger log = LoggerFactory.getLogger(NacosConfig.class);

    private String serverAddr;
    private String namespace;
    private String group;
    private String dataId;
    private boolean nacosEnabled;

    private ConfigService configService;

    @PostConstruct
    public void init() {
        // 直接从系统属性和环境变量读取配置，确保可靠性
        this.serverAddr = getConfig("nacos.server-addr", "127.0.0.1:8848");
        this.namespace = getConfig("nacos.namespace", "");
        this.group = getConfig("nacos.group", "DEFAULT_GROUP");
        this.dataId = getConfig("nacos.data-id", "RAGTranslationApplication-docker.yml");
        this.nacosEnabled = Boolean.parseBoolean(getConfig("nacos.enabled", "false"));

        log.info("========================================");
        log.info("NacosConfig 初始化");
        log.info("  server-addr: {}", serverAddr);
        log.info("  namespace: [{}]", namespace);
        log.info("  group: {}", group);
        log.info("  data-id: {}", dataId);
        log.info("  enabled: {}", nacosEnabled);
        log.info("========================================");

        if (!nacosEnabled) {
            log.info("Nacos配置中心未启用，使用本地配置");
            return;
        }

        log.info("Nacos配置中心已启用，尝试连接...");

        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", namespace != null && !namespace.isEmpty() ? namespace : "");
            properties.put("group", group);

            // 设置gRPC地址
            String grpcAddr = serverAddr.split(":")[0];
            properties.put("grpcAddr", grpcAddr);

            configService = NacosFactory.createConfigService(properties);

            // 尝试多次获取配置
            int retryCount = 0;
            int maxRetry = 3;
            String content = null;
            
            while (retryCount < maxRetry && content == null) {
                try {
                    content = configService.getConfig(dataId, group, 5000);
                    if (content == null && retryCount < maxRetry - 1) {
                        log.warn("Nacos配置获取失败，等待重试 ({}/{})", retryCount + 1, maxRetry);
                        Thread.sleep(2000);
                    }
                    retryCount++;
                } catch (Exception e) {
                    log.warn("获取Nacos配置异常: {}", e.getMessage());
                    if (retryCount < maxRetry - 1) {
                        Thread.sleep(2000);
                    }
                    retryCount++;
                }
            }

            if (content != null && !content.isEmpty()) {
                log.info("Nacos配置加载成功，数据ID: {}, 内容长度: {} characters", dataId, content.length());
                addConfigListener();
            } else {
                log.warn("Nacos配置未找到，数据ID: {}, 将使用本地配置", dataId);
            }

        } catch (NacosException e) {
            log.warn("Nacos配置中心连接失败，将使用本地配置: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Nacos配置中心初始化异常，将使用本地配置: {}", e.getMessage());
        }
    }

    private String getConfig(String key, String defaultValue) {
        // 先检查系统属性
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        // 环境变量：NACOS_SERVER_ADDR格式
        String envKey = key.toUpperCase().replace(".", "_");
        value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        // 也尝试原始格式
        value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        
        return defaultValue;
    }

    private void addConfigListener() {
        if (configService != null) {
            try {
                configService.addListener(dataId, group, new Listener() {
                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("Nacos配置已更新: {}", configInfo != null ? configInfo.length() + " characters" : "null");
                    }

                    @Override
                    public Executor getExecutor() {
                        return null;
                    }
                });
                log.info("Nacos配置监听器已注册");
            } catch (NacosException e) {
                log.error("注册Nacos配置监听器失败: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void destroy() {
        if (configService != null) {
            try {
                configService.shutDown();
                log.info("Nacos配置服务已关闭");
            } catch (NacosException e) {
                log.warn("关闭Nacos配置服务失败: {}", e.getMessage());
            }
        }
    }
}
