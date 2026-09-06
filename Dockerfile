# RAGTranslation Docker镜像
# 使用本地已有的Maven镜像（基于Ubuntu，兼容ONNX Runtime）
FROM maven:3.9.9-eclipse-temurin-17

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /timezone

# 安装curl（用于Docker Socket API调用）
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 设置默认profile为docker（使用Nacos配置中心）
ENV SPRING_PROFILES_ACTIVE=docker

# 复制JAR文件
COPY target/*.jar app.jar

# 复制环境变量文件
COPY .env /app/.env

EXPOSE 8000

# 启动应用（支持通过SPRING_PROFILES_ACTIVE环境变量覆盖profile）
# 禁用Java系统代理以解决SSL连接问题
# 强制禁用 Java 11+ JdkHttpClient 的代理
# 修复SSL证书问题：移除trustStore=NONE配置，使用Java默认证书库
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dhttp.proxyHost=", "-Dhttp.proxyPort=", "-Dhttps.proxyHost=", "-Dhttps.proxyPort=", "-Djava.net.useSystemProxies=false", "-Dhttp.nonProxyHosts=*", "-Djdk.httpclient.proxySelector.disableDynamicProxyDiscovery=true", "-Djdk.httpclient.allowRestrictedHeaders=Connection,Proxy-Authenticate,Proxy-Authorization", "-Djdk.httpclient.connectionPool.size=20", "-Djdk.httpclient.keepAlive.timeout=60", "-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3", "-Djdk.tls.client.cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "-jar", "app.jar"]