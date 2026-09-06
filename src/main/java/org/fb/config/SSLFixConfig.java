package org.fb.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.security.Security;
import java.util.concurrent.TimeUnit;

@Configuration
public class SSLFixConfig {
    private static final Logger log = LoggerFactory.getLogger(SSLFixConfig.class);

    @PostConstruct
    public void initSSLFix() {
        try {
            // 禁用Java系统的代理检测，强制直接连接
            System.setProperty("http.proxyHost", "");
            System.setProperty("http.proxyPort", "");
            System.setProperty("https.proxyHost", "");
            System.setProperty("https.proxyPort", "");
            System.setProperty("ftp.proxyHost", "");
            System.setProperty("ftp.proxyPort", "");
            System.setProperty("socksProxyHost", "");
            System.setProperty("socksProxyPort", "");
            
            // 禁用系统代理检测
            System.setProperty("java.net.useSystemProxies", "false");
            
            // 设置默认的HTTP代理为空，强制不使用代理
            System.setProperty("http.nonProxyHosts", "*");
            
            // 禁用代理自动配置
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            
            // 禁用 Java 11+ 原生 HttpClient 的代理设置
            System.setProperty("jdk.httpclient.proxySelector.disableDynamicProxyDiscovery", "true");
            System.setProperty("jdk.httpclient.connectionPool.size", "20");
            System.setProperty("jdk.httpclient.keepAlive.timeout", "60");
            
            // 强制禁用 JdkHttpClient 的代理（关键修复）
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Connection, Proxy-Authenticate, Proxy-Authorization");
            
            log.info("SSL代理配置已禁用，强制直接连接模式");
            log.info("当前系统代理设置检查:");
            log.info("  http.proxyHost: {}", System.getProperty("http.proxyHost"));
            log.info("  https.proxyHost: {}", System.getProperty("https.proxyHost"));
            log.info("  java.net.useSystemProxies: {}", System.getProperty("java.net.useSystemProxies"));
            log.info("  http.nonProxyHosts: {}", System.getProperty("http.nonProxyHosts"));
            log.info("  jdk.httpclient.proxySelector.disableDynamicProxyDiscovery: {}", 
                System.getProperty("jdk.httpclient.proxySelector.disableDynamicProxyDiscovery"));
            
        } catch (Exception e) {
            log.error("SSL代理配置初始化失败", e);
        }
    }
}