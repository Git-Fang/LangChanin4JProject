# 使用Ubuntu官方镜像，手动安装OpenJDK 17，避免依赖特定JDK镜像源
FROM ubuntu:22.04 AS builder

WORKDIR /app

# 配置国内阿里云Ubuntu软件源
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \ \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

# 安装OpenJDK 17和Maven
RUN apt-get update && apt-get install -y \ \
    openjdk-17-jdk \ \
    maven \ \
    && rm -rf /var/lib/apt/lists/*

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

# 使用Ubuntu官方镜像，手动安装JRE 17
FROM ubuntu:22.04

WORKDIR /app

# 配置国内阿里云Ubuntu软件源
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \ \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

# 安装OpenJDK 17 JRE和curl
RUN apt-get update && apt-get install -y \ \
    openjdk-17-jre \ \
    curl \ \
    && rm -rf /var/lib/apt/lists/*

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

# 明确绑定到所有网卡
ENTRYPOINT ["java", "-jar", "-Dserver.address=0.0.0.0", "-Dserver.port=8000", "app.jar"]