# 使用基于Ubuntu的镜像，避免musl libc与glibc兼容性问题
FROM eclipse-temurin:17-jdk-ubuntu AS builder

WORKDIR /app

# 安装Maven和必要的依赖库
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# 配置Maven使用阿里云仓库
RUN mkdir -p /root/.m2 \
    && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">' > /root/.m2/settings.xml \
    && echo '    <mirrors>' >> /root/.m2/settings.xml \
    && echo '        <mirror>' >> /root/.m2/settings.xml \
    && echo '            <id>aliyunmaven</id>' >> /root/.m2/settings.xml \
    && echo '            <mirrorOf>*</mirrorOf>' >> /root/.m2/settings.xml \
    && echo '            <name>阿里云公共仓库</name>' >> /root/.m2/settings.xml \
    && echo '            <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml \
    && echo '        </mirror>' >> /root/.m2/settings.xml \
    && echo '    </mirrors>' >> /root/.m2/settings.xml \
    && echo '    <profiles>' >> /root/.m2/settings.xml \
    && echo '        <profile>' >> /root/.m2/settings.xml \
    && echo '            <id>aliyun</id>' >> /root/.m2/settings.xml \
    && echo '            <repositories>' >> /root/.m2/settings.xml \
    && echo '                <repository>' >> /root/.m2/settings.xml \
    && echo '                    <id>aliyunmaven</id>' >> /root/.m2/settings.xml \
    && echo '                    <name>aliyunmaven</name>' >> /root/.m2/settings.xml \
    && echo '                    <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml \
    && echo '                </repository>' >> /root/.m2/settings.xml \
    && echo '            </repositories>' >> /root/.m2/settings.xml \
    && echo '        </profile>' >> /root/.m2/settings.xml \
    && echo '    </profiles>' >> /root/.m2/settings.xml \
    && echo '    <activeProfiles>' >> /root/.m2/settings.xml \
    && echo '        <activeProfile>aliyun</activeProfile>' >> /root/.m2/settings.xml \
    && echo '    </activeProfiles>' >> /root/.m2/settings.xml \
    && echo '</settings>' >> /root/.m2/settings.xml

# 先复制pom.xml，缓存依赖下载
COPY pom.xml .

# 下载所有依赖
RUN mvn dependency:go-offline -DskipTests || true

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests

# 使用基于Ubuntu的JRE镜像
FROM eclipse-temurin:17-jre-ubuntu

WORKDIR /app

# 安装curl用于健康检查和onnxruntime可能需要的依赖
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

# 明确绑定到所有网卡
ENTRYPOINT ["java", "-jar", "-Dserver.address=0.0.0.0", "-Dserver.port=8000", "app.jar"]