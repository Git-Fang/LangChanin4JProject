# 🚀 RAGTranslation Docker 部署解决方案

## 快速部署

由于遇到镜像拉取问题，以下是三套解决方案：

### 方案1：轻量级独立运行
```bash
docker-compose -f docker-compose-standalone.yml up -d
```

### 方案2：无数据库依赖运行（仅应用）
```bash
# 本地打包
mvn clean package -DskipTests

# 直接构建镜像
docker build -t ragtranslation-app .

# 运行应用（仅运行应用，需要您本地数据库）
docker run -p 8000:8000 ragtranslation-app
```

### 方案3：完全本地运行
```bash
# 本地运行Spring Boot应用（依赖本地数据库）
mvn spring-boot:run
```

## 关键文件

1. **`Dockerfile`** - 项目镜像构建文件
2. **`docker-compose-standalone.yml`** - 简化版Compose配置
3. **`deploy-desktop.bat`** - 容错性强的部署脚本
4. **`docker-compose.yml`** - 完整版（需要镜像拉取正常）

## 访问地址

- 主页面：`http://localhost:8000/unified.html`
- 首页：`http://localhost:8000/`
- RabbitMQ管理：`http://localhost:15672`

## 服务端口

- MySQL: 3306 (root/root)
- Redis: 6379
- MongoDB: 27017
- RabbitMQ: 5672 (管理端口15672)
- Qdrant: 6333/6334
- 应用: 8000

## 故障处理

如果镜像拉取失败，建议使用方案2或3（完全本地运行）。

运行 `./health-check.bat` 检查服务状态。运行 `./stop-desktop.bat` 停止服务。按 Ctrl+C 停止应用运行。