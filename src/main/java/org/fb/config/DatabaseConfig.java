package org.fb.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public DataSource dataSource() {
        // 修复 JDBC URL：将 ; 替换为 &（如果 URL 中包含 ; 且不是第一个参数）
        String fixedUrl = fixJdbcUrl(dbUrl);

        if (!fixedUrl.equals(dbUrl)) {
            log.warn("检测到 JDBC URL 使用了错误的参数分隔符 ';'，已自动修复为 '&'");
            log.info("原始 URL: {}", dbUrl);
            log.info("修复后 URL: {}", fixedUrl);
        }

        log.info("正在创建数据源，URL: {}", fixedUrl);

        return DataSourceBuilder.create()
                .url(fixedUrl)
                .username(username)
                .password(password)
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
    }

    /**
     * 修复 JDBC URL 中的参数分隔符
     * 将 ; 替换为 &（适用于 MySQL JDBC URL）
     */
    private String fixJdbcUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        // 查找问号后的参数部分
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex == -1) {
            // 没有参数，直接返回
            return url;
        }

        String baseUrl = url.substring(0, questionMarkIndex + 1);
        String params = url.substring(questionMarkIndex + 1);

        // 如果参数中包含 ; 但没有 &，则替换
        if (params.contains(";")) {
            // 检查是否包含 &，如果包含则可能是混合使用，需要统一
            params = params.replace(";", "&");
        }

        return baseUrl + params;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}