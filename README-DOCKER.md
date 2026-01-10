# RAGTranslation Docker部署指南

## 🚀 快速开始

### 1. 准备工作

- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- 确保Docker Desktop正在运行
- 克隆或下载本代码到本地

### 2. 环境变量配置（可选）

将 `.env.example` 复制为 `.env` 并填入必要的API密钥：

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入你的API密钥：
- DeepSeek_API_KEY
- KIMI_API_KEY
- DASHSCOPE_API_KEY
- BAIDU_MAP_API_KEY

### 3. 一键部署

Windows系统双击运行：
```cmd
deploy-desktop.bat
```

或使用命令行：
```cmd
./deploy-desktop.bat
```

### 4. 访问服务

等待部署完成后，可以通过以下地址访问：

- 统一访问页面：`http://localhost:8000/unified.html`
- 主页：`http://localhost:8000/`
- RABBITMQ管理：`http://localhost:15672` (admin/admin)

## 📋 服务组件

| 服务 | 端口 | 说明 |
|------|------|------|
| 应用服务 | 8000 | RAGTranslation应用 |
| MySQL | 3306 | 关系型数据库 |
| Redis | 6379 | 缓存数据库 |
| RabbitMQ | 5672/15672 | 消息队列管理 |
| MongoDB | 27017 | 文档数据库 |
| Qdrant | 6333/6334 | 向量数据库 |

## 🔧 管理命令

### 健康检查
```cmd
health-check.bat
```

### 停止服务
```cmd
stop-desktop.bat
```

### 查看日志
```cmd
# 实时查看应用日志
docker-compose logs -f app

# 查看所有服务日志
docker-compose logs -f
```

### 重启服务
```cmd
# 重启所有服务
docker-compose restart

# 重启单个服务
docker-compose restart app
```

## 📊 Docker Desktop监控

- 打开Docker Desktop界面
- 点击左侧"Containers"菜单
- 找到`ragtranslation-app`等服务
- 可以查看容器状态、日志、资源使用情况

## 🚀 重新构建

如果修改了代码或配置，需要重新构建镜像：

```cmd
docker-compose build --no-cache
docker-compose up -d
```

## 🛠️ 故障排查

### 常见问题

1. **端口占用**：如果端口被占用，请在`docker-compose.yml`中修改对应端口映射
2. **镜像拉取失败**：请检查网络连接，或手动拉取指定镜像
3. **应用启动失败**：查看日志 `docker-compose logs app`

### 查看容器状态
```cmd
docker-compose ps
```

### 进入容器调试
```cmd
# 进入应用容器
docker exec -it ragtranslation-app bash

# 进入数据库容器
docker exec -it ragtranslation-mysql mysql -u root -p
```

## 👥 团队协作

注意 `.env` 文件包含敏感信息，**不要提交到版本控制**。每个开发者应创建自己的`.env`文件。

## 📖 注意事项

1. 首次启动会拉取所有镜像，耗时较长
2. 数据库数据使用Docker卷持久化，容器删除数据不会丢失
3. 建议在正式使用前备份重要数据
4. 生产环境请使用更安全的数据库密码
5. 如果需要修改配置，请编辑`application-docker.yml`文件并重新构建镜像