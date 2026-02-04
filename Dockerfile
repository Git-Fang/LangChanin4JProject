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

# 复制本地知识库目录（.agent 和 knowledge）
# 这些目录用于本地知识库检索功能
COPY .agent /app/.agent
COPY knowledge /app/knowledge

EXPOSE 8000

# 启动应用（支持通过SPRING_PROFILES_ACTIVE环境变量覆盖profile）
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]