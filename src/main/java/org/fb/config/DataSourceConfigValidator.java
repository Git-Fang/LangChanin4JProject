package org.fb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;

/**
 * 数据源配置验证器
 * 在应用启动时验证数据源配置是否正确
 */
@Configuration
public class DataSourceConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfigValidator.class);

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @PostConstruct
    public void validateConfig() {
        log.info("=== 数据源配置验证开始 ===");

        if (dbUrl == null || dbUrl.isEmpty()) {
            log.error("数据源 URL 未配置！请检查 application.yml 或 Nacos 配置");
            throw new IllegalStateException("数据源 URL 未配置");
        }

        log.info("数据源 URL: {}", dbUrl);
        log.info("数据源用户名: {}", username);

        // 验证 URL 格式
        if (!dbUrl.startsWith("jdbc:mysql://")) {
            log.warn("数据源 URL 不是标准的 MySQL JDBC URL 格式");
        }

        // 检查 URL 中是否包含错误的分隔符
        int questionMarkIndex = dbUrl.indexOf('?');
        if (questionMarkIndex != -1) {
            String params = dbUrl.substring(questionMarkIndex + 1);

            if (params.contains(";")) {
                log.error("⚠️ 检测到 JDBC URL 使用了错误的参数分隔符 ';'！");
                log.error("这会导致 'Unsupported character encoding' 错误");
                log.error("请将 URL 中的 ';' 替换为 '&'");
                log.error("错误示例: jdbc:mysql://host:3306/db?characterEncoding=UTF-8;serverTimezone=Asia/Shanghai");
                log.error("正确示例: jdbc:mysql://host:3306/db?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");

                // 抛出异常阻止应用启动，强制修复配置
                throw new IllegalStateException(
                    "JDBC URL 配置错误：使用了 ';' 作为参数分隔符，请使用 '&' 替换。\n" +
                    "当前 URL: " + dbUrl + "\n" +
                    "修复后的 URL: " + dbUrl.replace(";", "&")
                );
            }
        }

        // 检查关键参数
        if (!dbUrl.contains("characterEncoding=")) {
            log.warn("URL 中未指定 characterEncoding 参数，建议添加: characterEncoding=UTF-8");
        }

        if (!dbUrl.contains("serverTimezone=")) {
            log.warn("URL 中未指定 serverTimezone 参数，建议添加: serverTimezone=Asia/Shanghai");
        }

        if (!dbUrl.contains("useSSL=")) {
            log.warn("URL 中未指定 useSSL 参数");
        }

        log.info("=== 数据源配置验证完成 ===");
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        log.info("应用上下文已刷新，数据源配置验证通过");
    }
}
