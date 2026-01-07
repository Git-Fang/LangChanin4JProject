# 使用官方eclipse-temurin镜像，避免国内镜像源访问权限问题
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 替换Alpine软件源为国内阿里云源，加速软件包下载
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装Maven
RUN apk add --no-cache maven

# 配置Maven使用阿里云仓库
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml << 'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>aliyunmaven</id>
            <mirrorOf>*</mirrorOf>
            <name>阿里云公共仓库</name>
            <url>https://maven.aliyun.com/repository/public</url>
        </mirror>
    </mirrors>
    <profiles>
        <profile>
            <id>aliyun</id>
            <repositories>
                <repository>
                    <id>aliyunmaven</id>
                    <name>aliyunmaven</name>
                    <url>https://maven.aliyun.com/repository/public</url>
                </repository>
            </repositories>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>aliyun</activeProfile>
    </activeProfiles>
</settings>
EOF

# 先复制pom.xml，缓存依赖下载
COPY pom.xml .

# 下载所有依赖
RUN mvn dependency:go-offline -DskipTests || true

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试
RUN mvn clean package -DskipTests

# 使用官方JRE镜像
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]
