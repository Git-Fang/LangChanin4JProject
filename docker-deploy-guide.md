# RAG翻译系统 - Docker部署指南

## 快速部署

### Windows 用户
双击运行 `deploy.bat` 即可自动部署到Docker Desktop。

### Linux/macOS 用户
```bash
chmod +x deploy.sh
./deploy.sh
```

## 部署特性

### 1. 服务架构
- **应用服务**：RAG Translation 主应用
- **MySQL 8.0**：应用数据存储
- **MongoDB 7.0**：聊天历史存储
- **Redis（RedisStack）**：缓存和RedisInsight管理界面
- **RabbitMQ 3**：消息队列，带管理界面
- **Qdrant**：向量数据库，支持RAG检索

### 2. 端口映射
| 服务 | 本地端口 | 容器端口 | 备注 |
|------|----------|----------|------|
| RAG Translation | 8000 | 8000 | 主应用端口 |
| MySQL | 3306 | 3306 | 数据库 |
| MongoDB | 27017 | 27017 | NoSQL数据库 |
| Redis | 6379 | 6379 | 缓存 |
| RedisInsight | 8001 | 8001 | Redis管理界面 |
| RabbitMQ AMQP | 5672 | 5672 | 消息队列 |
| RabbitMQ Management | 15672 | 15672 | RabbitMQ管理界面 |
| Qdrant HTTP | 6333 | 6333 | 向量数据库 |
| Qdrant gRPC | 6334 | 6334 | 向量数据库 |

### 3. 默认账户
- **MySQL**:用户 `rag_user`，密码 `rag_password`
- **RabbitMQ Management**:用户 `admin`，密码 `admin123`
- **MongoDB**:无认证，数据库 `chat_db`
- **Redis**:无认证

## 部署步骤（手动）

### 1. 环境准备
确保已安装：
- Docker Desktop（Windows/macOS）或 Docker Engine（Linux）
- Docker Compose

### 2. 配置文件
```bash
# 复制环境变量示例文件（如需要）
cp .env.example .env

# 编辑配置（可选）
nano .env
```

### 3. 部署命令
```bash
# 构建并启动所有服务
docker compose build
docker compose up -d

# 查看日志
docker compose logs -f

# 查看服务状态
docker compose ps
```

### 4. 访问应用
- **Web界面**: http://localhost:8000/index.html
- **API文档**: http://localhost:8000/doc.html
- **RabbitMQ管理**: http://localhost:15672

## 数据持久化

所有服务的数据都会持久化到本地卷：
- `mysql-data`: MySQL数据
- `mongo-data`: MongoDB数据
- `redis-data`: Redis数据
- `rabbitmq-data`: RabbitMQ数据
- `qdrant-data`: Qdrant向量数据

## 运维命令

### 查看日志
```bash
# 所有服务
docker compose logs -f

# 特定服务
docker compose logs -f rag-translation
```

### 停止服务
```bash
# 停止并保留数据
docker compose stop

# 停止并清理数据
docker compose down --volumes
```

### 重启服务
```bash
# 重启特定服务
docker compose restart rag-translation

# 重启所有服务
docker compose restart
```

## 故障排除

### 1. 端口冲突
如果端口已被占用，修改 `docker-compose.yml` 中的端口映射。

### 2. 构建失败
检查网络连接，可能需要配置Docker镜像加速器。

### 3. 服务启动失败
查看服务日志：
```bash
docker compose logs [服务名]
```

### 4. 权限问题（Linux）
确保当前用户在docker组中：
```bash
sudo usermod -aG docker $USER
```

## 性能优化

### JVM参数
在 `docker-compose.yml` 中调整：
```yaml
environment:
  - JAVA_OPTS=-Xms512m -Xmx2048m -XX:+UseG1GC
```

### 资源限制
可在 `deploy.sh` 或 `deploy.bat` 中添加资源限制参数。

## 安全建议

1. **生产环境**请修改默认密码
2. **API密钥**通过 `.env` 文件配置
3. **文档目录**建议在生产环境移除
4. **日志级别**在生产环境设置为 WARN 或 ERROR

## 联系支持

如有问题，请查看服务日志或提交Issue。