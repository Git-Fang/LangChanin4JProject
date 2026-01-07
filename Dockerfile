# 使用国内Docker镜像源拉取基础镜像
FROM registry.cn-hangzhou.aliyuncs.com/library/eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 替换为国内阿里云镜像源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装Maven
RUN apk add --no-cache maven

# 配置Maven使用国内仓库（添加多个仓库提高下载成功率）
RUN mkdir -p /root/.m2 && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyunmaven</id><mirrorOf>*</mirrorOf><name>阿里云公共仓库</name><url>https://maven.aliyun.com/repository/public</url></mirror><mirror><id>repo1</id><mirrorOf>central</mirrorOf><name>Central Repository</name><url>https://repo1.maven.org/maven2/</url></mirror><mirror><id>spring-milestones</id><mirrorOf>spring-milestones</mirrorOf><name>Spring Milestones</name><url>https://repo.spring.io/milestone</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先复制pom.xml，缓存依赖下载
COPY pom.xml .

# 下载所有依赖，设置超时时间
RUN mvn dependency:go-offline -DskipTests -Dmaven.wagon.http.connectionTimeout=60000 -Dmaven.wagon.http.readTimeout=60000

# 复制源代码
COPY src ./src

# 执行Maven构建，跳过测试，设置超时时间
RUN mvn clean package -DskipTests -Dmaven.wagon.http.connectionTimeout=60000 -Dmaven.wagon.http.readTimeout=60000

# 使用国内Docker镜像源拉取JRE基础镜像
FROM registry.cn-hangzhou.aliyuncs.com/library/eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建好的jar文件
COPY --from=builder /app/target/RAGTranslation4-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "app.jar"]