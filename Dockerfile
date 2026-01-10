# 修复版Dockerfile，添加ONNX Runtime依赖
FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM openjdk:17-jre-slim

# 安装ONNX Runtime所需的库
RUN apt-get update && apt-get install -y \\
    libc6 \\
    libgomp1 \\
    libstdc++6 \\
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

CMD ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]

EXPOSE 8000