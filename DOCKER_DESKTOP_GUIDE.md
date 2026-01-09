# RAG Translation System - Docker Desktop 部署指南

## 概述

本文档提供了在本地 Docker Desktop 环境下部署和运行 RAG 增强型翻译系统的详细说明。

## 前提条件

### 必需软件

1. **Docker Desktop**
   - Windows: [Docker Desktop for Windows](https://docs.docker.com/docker-for-windows/install/)
   - macOS: [Docker Desktop for Mac](https://docs.docker.com/docker-for-mac/install/)
   - Linux: 安装 Docker Engine 和 Docker Compose

2. **系统资源要求**
   - 内存：至少 8GB 可用内存（推荐 16GB）
   - 存储：至少 50GB 可用磁盘空间
   - CPU：4 核心以上

### 环境配置

1. **启用 Kubernetes（可选）**：如果您希望后续使用 Kubernetes，可在 Docker Desktop 中启用
2. **资源分配**：确保 Docker Desktop 有足够资源分配（推荐 CPU：4 核，内存：8GB）

## 快速开始

### 1. 获取项目代码

```bash
git clone <repository-url>
cd RAGTranslation--mcp--docker
```

### 2. 配置环境变量

```bash
# Linux/MacOS
cp .env.example .env

# Windows
copy .env.example .env
```

编辑 `.env` 文件，配置必要的 API Keys：

```env
# API密钥配置（必需）
DeepSeek_API_KEY=your_deepseek_api_key_here
KIMI_API_KEY=your_kimi_api_key_here
DASHSCOPE_API_KEY=your_dashscope_api_key_here
BAIDU_MAP_API_KEY=your_baidu_map_api_key

# 数据库配置（使用默认值即可）
DB_URL=jdbc:mysql://mysql:3306/rag_translation?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=root

# 服务端口
SERVER_PORT=8000
```

### 3. 选择部署方式

#### Windows 用户使用批处理脚本

双击运行或执行：
```cmd
deploy-desktop.bat
```

#### Linux/MacOS 用户使用 Shell 脚本

执行：
```bash
./deploy-desktop.sh
```

### 4. 验证部署

打开浏览器访问：
- **应用界面**：http://localhost:8000/index.html
- **API文档**：http://localhost:8000/doc.html
- **健康检查**：http://localhost:8000/actuator/health

服务端口：
- 应用端口：8000
- MySQL：3306
- MongoDB：27017
- Redis：6379
- Qdrant：6333
- Ollama：11434

## 服务管理

使用管理脚本来控制服务：

### 基本命令

```bash
# 启动服务
./manage-desktop.sh start

# 停止服务
./manage-desktop.sh stop

# 重启服务
./manage-desktop.sh restart

# 查看状态
./manage-desktop.sh status

# 实时日志
./manage-desktop.sh logs -f

# 健康检查
./manage-desktop.sh health

# 进入容器
./manage-desktop.sh shell
```

### Windows 用户
```cmd
# 启动服务
manage-desktop.bat start

# 查看状态
manage-desktop.bat status

# 其他命令类似...
```

## 功能测试

### 1. 基本翻译功能

使用 curl 或 API 文档界面测试翻译接口：

```bash
curl -X POST http://localhost:8000/translate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Hello, how are you?",
    "sourceLanguage": "en",
    "targetLanguage": "zh"
  }'
```

### 2. 文档上传功能

测试文档上传（需要安装 curl）：

```bash
curl -X POST http://localhost:8000/api/documents/upload \
  -F "file=@/path/to/your/document.pdf" \
  -F "userId=1"
```

### 3. 聊天记录查询

测试聊天功能：

```bash
curl -X POST http://localhost:8000/mcp/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "channelId": "ch456",
    "message": "如何使用翻译功能？"
  }'
```

## 故障排查

### 常见问题

#### 1. 端口冲突
如果端口被占用，修改 `docker-compose.desktop.yml` 中的端口映射：

```yaml
services:
  rag-translation:
    ports:
      - "8001:8000"  # 改为其他端口
```

#### 2. 内存不足
在 Docker Desktop 设置中增加可用内存，或减少各服务的内存限制：

```yaml
# 在 docker-compose.desktop.yml 中添加内存限制
services:
  mysql:
    mem_limit: 512m
  rag-translation:
    environment:
      - JAVA_OPTS=-Xms256m -Xmx512m
```

#### 3. 构建失败
- 检查网络连接，确保能访问Maven仓库
- 清理构建缓存：
  ```bash
  docker-compose -f docker-compose.desktop.yml build --no-cache
  ```

#### 4. 服务无法连接数据库
检查容器网络和服务依赖：
```bash
# 查看服务状态
docker-compose -f docker-compose.desktop.yml ps

# 检查网络连接
docker exec rag-translation nc -zv mysql 3306
```

#### 5. 日志分析
查看特定服务的日志：
```bash
# 查看应用日志
docker-compose -f docker-compose.desktop.yml logs rag-translation

# 查看数据库日志（需要时）
docker-compose -f docker-compose.desktop.yml logs mysql

# 实时日志
docker-compose -f docker-compose.desktop.yml logs -f rag-translation
```

### 诊断工具

使用内置的诊断脚本：
```bash
./manage-desktop.sh health
```

或使用 Docker Desktop 的 CLI：
```bash
# 检查容器健康状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 检查资源使用
docker stats --no-stream
```

## 数据持久化

### 数据存储位置
所有数据通过 Docker Volume 持久化，即使容器重启也不会丢失：

- **MySQL 数据**：`mysql-data`
- **MongoDB 数据**：`mongo-data`
- **Redis 数据**：`redis-data`
- **Qdrant 数据**：`qdrant-data`
- **应用日志**：`./logs`（相对路径）
- **上传文件**：`./uploads`（相对路径）

### 备份数据

使用管理脚本备份所有数据：
```bash
./manage-desktop.sh backup
```

或使用 Docker 命令手动备份：
```bash
# 备份 MySQL
docker exec rag-mysql mysqldump -uroot -proot rag_translation > backup_mysql.sql

# 备份 MongoDB
docker exec rag-mongo mongodump --db chat_db --archive=/tmp/mongo.backup
docker cp rag-mongo:/tmp/mongo.backup ./
```

### 恢复数据
```bash
# 恢复 MySQL
docker exec -i rag-mysql mysql -uroot -proot rag_translation < backup_mysql.sql

# 恢复 MongoDB
docker cp ./mongo.backup rag-mongo:/tmp/
docker exec rag-mongo mongorestore --db chat_db --archive=/tmp/mongo.backup
```

## 性能优化

### JVM 参数调优
根据服务器资源调整 Java 参数：

```yaml
environment:
  - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication
  - SERVER_PORT=8000
```

### 数据库连接池优化
在 `application-desktop.yml` 中配置连接池：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

### 缓存配置
启用 Redis 缓存：

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000
      key-prefix: rag:translation:
```

## 高级配置

### 环境切换
支持多环境配置，通过环境变量切换：

```yaml
# 开发环境
docker-compose -f docker-compose.desktop.yml up -d

# 生产环境（需要单独配置文件）
docker-compose -f docker-compose.prod.yml up -d
```

### 服务扩展
可以调整各服务的实例数量：

```yaml
services:
  rag-translation:
    deploy:
      replicas: 3  # 运行3个实例（需要Docker Swarm模式）
```

### 安全配置
在生产环境中添加 HTTPS 支持：

```yaml
# 使用 Nginx 反向代理作为 SSL 终端
services:
  nginx:
    image: nginx:alpine
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./ssl:/etc/nginx/ssl
```

## 开发调试

### 热重载开发
挂载本地代码进行开发调试：

```yaml
services:
  rag-translation:
    volumes:
      - ./src:/app/src
      - ./target:/app/target
```

### 调试 JVM
添加远程调试支持：

```yaml
environment:
  - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

### API 测试
安装 [Postman](https://www.postman.com/) 或使用 curl 测试接口：

```bash
# 上传文档测试
curl -X POST http://localhost:8000/api/documents/upload \
  -F "file=@test.pdf" \
  -F "userId=1" \
  -v
```

## 更新升级

### 获取最新代码
```bash
git pull origin main
```

### 重新构建并部署
```bash
./manage-desktop.sh update
```

### 版本管理
为容器添加版本标签：
```bash
docker tag rag-translation rag-translation:v1.0.0
docker tag rag-mysql rag-mysql:v1.0.0
```

## 监控和日志

### 日志轮转
在 `manage-desktop.sh` 中配置定期备份：

```bash
#!/bin/bash
# 每天凌晨2点清理30天前的日志
0 2 * * * find ./logs -name "*.log" -type f -mtime +30 -delete
```

### 性能监控
使用 Spring Boot Actuator：

```bash
# 查看应用指标
curl http://localhost:8000/actuator/metrics

# 查看JVM信息
curl http://localhost:8000/actuator/beans
```

## 卸载清理

### 完全卸载
```bash
# 停止并删除所有容器和数据卷
docker-compose -f docker-compose.desktop.yml down -v --remove-orphans

# 删除镜像
docker rmi $(docker images -q rag-*)

# 清理未使用的资源
docker system prune -af
docker volume prune -f
```

### 保留数据卸载
```bash
# 停止但不删除数据卷
docker-compose -f docker-compose.desktop.yml down
```

## 技术支持

### 日志收集
遇到问题时，收集以下信息：

```bash
# 所有服务状态
docker-compose -f docker-compose.desktop.yml ps

# 应用详细日志
docker-compose -f docker-compose.desktop.yml logs --tail=100 rag-translation > app.log

# 系统资源状态
docker stats --all --no-stream > stats.log
```

### 社区支持
- 提交 Issue 到 GitHub 仓库
- 查看日志中的特定错误信息
- 检查各个服务的健康状态

## 许可证

请根据项目实际许可证信息进行填写。通常位于项目根目录的 LICENSE 文件中。

---

**注意**：本指南假设您使用的是最新版本的 Docker Desktop。不同版本可能在界面或功能上略有差异。