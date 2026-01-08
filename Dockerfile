# 第一阶段：构建阶段 - 使用Maven官方镜像（已包含JDK和Maven）
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# 配置Maven使用阿里云仓库（创建settings.xml文件）
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0' >> /root/.m2/settings.xml && \
    echo '  http://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '  <mirrors>' >> /root/.m2/settings.xml && \
    echo '    <mirror>' >> /root/.m2/settings.xml && \
    echo '      <id>aliyunmaven</id>' >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>*</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '      <name>阿里云公共仓库</name>' >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '    </mirror>' >> /root/.m2/settings.xml && \
    echo '  </mirrors>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# 先复制pom.xml，缓存依赖下载
COPY pom.xml .

# 下载所有依赖
RUN mvn dependency:go-offline -DskipTests || true

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests

# 第二阶段：运行阶段 - 使用轻量级JRE镜像
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装curl用于健康检查和libstdc++用于ONNX Runtime（Alpine使用apk）
RUN apk add --no-cache curl libstdc++

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

# 明确绑定到所有网卡
ENTRYPOINT ["java", "-jar", "-Dserver.address=0.0.0.0", "-Dserver.port=8000", "app.jar"]