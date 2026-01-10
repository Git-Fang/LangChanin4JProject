# RAGTranslation Docker镜像 - 多阶段构建
# 构建阶段
FROM maven:3-eclipse-temurin-17 AS build

WORKDIR /app

# 配置Maven阿里云镜像加速
RUN mkdir -p ~/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?><settings><mirrors><mirror><id>aliyun</id><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > ~/.m2/settings.xml

# 复制pom.xml并下载依赖（利用Docker缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B 2>/dev/null || true

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -q

# 运行阶段
FROM eclipse-temurin:17-jre

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 复制JAR文件
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8000

# 启动应用，使用docker配置文件
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
