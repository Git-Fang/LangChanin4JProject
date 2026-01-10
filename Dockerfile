# RAGTranslation Docker镜像
# 使用本地已有的Maven镜像（基于Ubuntu，兼容ONNX Runtime）
FROM maven:3-eclipse-temurin-17

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 复制JAR文件
COPY target/*.jar app.jar

EXPOSE 8000

# 启动应用
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
