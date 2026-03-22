# DocSummarizer Docker镜像
# 文档摘要总结服务

FROM eclipse-temurin:17-jre

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /timezone

WORKDIR /app

# 设置默认profile为standalone（本地模式，不需要外部服务）
ENV SPRING_PROFILES_ACTIVE=standalone

# 复制JAR文件
COPY target/*.jar app.jar

EXPOSE 8000

# 启动应用
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
