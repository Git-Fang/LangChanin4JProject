# RAG Translation System - Docker Desktop 适配版本

## 🎯 项目概述

这是一个专为 Docker Desktop 环境优化的 RAG 增强型翻译系统，支持多种 AI 模型（DeepSeek、Kimi、DashScope、Ollama）和上下文翻译功能。

### 核心特性

- ✅ **多模型翻译支持**：集成 DeepSeek、Kimi、DashScope 等主流 LLM
- ✅ **RAG 增强翻译**：使用 Qdrant 向量数据库提供上下文感知的翻译
- ✅ **MCP 协议支持**：支持大模型上下文协议和工具调用
- ✅ **文档处理**：支持文档上传、向量化存储和智能问答
- ✅ **流式输出**：支持 SSE 实时流式响应
- ✅ **全栈容器化**：一键部署所有依赖服务

## 🚀 快速开始

### 1. 系统要求

- **Docker Desktop** (Windows/Mac) 或 Docker Engine + Compose (Linux)
- **内存**：≥ 8GB 推荐 16GB
- **存储空间**：≥ 50GB
- **网络**：可访问外部 API 服务

### 2. 获取项目

```bash
git clone <repository-url>
cd RAGTranslation--mcp--docker
```

### 3. 配置环境

#### Windows:
```cmd
copy .env.example .env
# 编辑 .env 文件，配置 API Keys
```

#### Linux/MacOS:
```bash
cp .env.example .env
# 编辑 .env 文件，配置 API Keys
```

### 4. 一键部署

#### Windows:
```cmd
deploy-desktop.bat
```

#### Linux/MacOS:
```bash
./deploy-desktop.sh
```

### 5. 访问应用

等待部署完成后，浏览器访问：
- 应用界面：http://localhost:8000/index.html
- API 文档：http://localhost:8000/doc.html
- 健康检查：http://localhost:8000/actuator/health

## 📋 项目结构

```
RAGTranslation--mcp--docker/
├── src/                          # 应用程序源代码
│   ├── main/
│   │   ├── java/org/fb/         # Java 业务代码
│   │   └── resources/
│   │       ├── application.yml  # 默认配置（已适配Docker Desktop）
│   │       └── static/          # 前端静态文件
│   └── test/                    # 测试代码
├── sql/
│   └── init.sql                 # 数据库初始化脚本
├── logs/                        # 应用日志（自动生成）
├── uploads/                     # 文件上传目录（自动生成）
├── backups/                     # 备份文件（自动生成）
├── docker-compose.desktop.yml   # Docker Desktop 配置文件
├── Dockerfile.desktop           # 应用镜像构建文件
├── application-desktop.yml      # Docker Desktop 应用配置
├── deploy-desktop.sh/bat       # 一键部署脚本
├── manage-desktop.sh/bat       # 管理服务脚本
├── test-build.sh/bat           # 构建测试脚本
└── .env.example                # 环境变量示例
```

## 🔧 服务管理

使用管理脚本来控制服务：

```bash
# 启动服务
./manage-desktop.sh start

# 停止服务
./manage-desktop.sh stop

# 重启服务
./manage-desktop.sh restart

# 查看日志
./manage-desktop.sh logs -f

# 健康检查
./manage-desktop.sh health

# 查看状态
./manage-desktop.sh status

# 清理所有（包括数据）
./manage-desktop.sh clean

# 进入容器
./manage-desktop.sh shell
```

## 🔍 故障排查

### 构建失败
- 检查网络连接和 Maven 仓库访问
- 运行测试构建：`./test-build.sh`
- 查看详细构建日志：`docker-compose -f docker-compose.desktop.yml build --no-cache`

### 端口冲突
修改 `docker-compose.desktop.yml` 中的端口映射：
```yaml
ports:
  - "8001:8000"  # 改为其他端口
```

### 内存不足
在 Docker Desktop 设置中增加内存限制，或调整服务配置：
```yaml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m
```

### API 连接问题
- 确认 API Keys 已正确配置在 `.env` 文件中
- 检查外部网络连接
- 查看应用日志确认 API 调用状态

## 📊 性能监控

通过 Spring Boot Actuator 监控应用：

```bash
# 健康检查
curl http://localhost:8000/actuator/health

# 系统指标
curl http://localhost:8000/actuator/metrics

# 环境信息
curl http://localhost:8000/actuator/env

# 数据库连接
curl http://localhost:8000/actuator/health/db
```

## 💾 数据持久化

所有数据通过 Docker Volume 持久化：

| 服务      | 数据挂载点 | 说明 |
|-----------|-----------|------|
| MySQL     | mysql-data | 关系型数据 |
| MongoDB   | mongo-data | 文档数据 |
| Redis     | redis-data | 缓存数据 |
| Qdrant    | qdrant-data | 向量数据 |
| 应用日志  | ./logs | 本地文件系统 |
| 上传文件  | ./uploads | 本地文件系统 |

备份数据：
```bash
./manage-desktop.sh backup
```

## 🌐 API 使用示例

### 基础翻译
```bash
curl -X POST http://localhost:8000/translate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Hello world",
    "sourceLanguage": "en",
    "targetLanguage": "zh",
    "useRAG": false
  }'
```

### RAG 翻译（上下文增强）
```bash
curl -X POST http://localhost:8000/translate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Initialize the ML pipeline",
    "sourceLanguage": "en",
    "targetLanguage": "zh",
    "useRAG": true,
    "context": "machine learning documentation"
  }'
```

### 文档上传
```bash
curl -X POST http://localhost:8000/api/documents/upload \
  -F "file=@document.pdf" \
  -F "userId=1"
```

### MCP 聊天
```bash
curl -X POST http://localhost:8000/mcp/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "channelId": "ch456",
    "message": "如何使用翻译功能？",
    "useTool": true
  }'
```

## 🔄 更新与维护

### 获取最新代码
```bash
git pull origin main
```

### 重新构建并部署
```bash
./manage-desktop.sh update
```

### 手动重建
```bash
docker-compose -f docker-compose.desktop.yml build --no-cache
docker-compose -f docker-compose.desktop.yml up -d
```

## 🛡️ 安全注意事项

1. **API Keys**：妥善保管 `.env` 文件中的 API Keys
2. **端口暴露**：确保仅开放必要的端口
3. **数据备份**：定期备份重要数据
4. **容器安全**：避免在生产环境中运行 QEMU 用户模式

## 📚 架构说明

### 服务架构
```
┌──────────────────────────────────────────────┐
│                  Web UI                      │
│          (http://localhost:8000)             │
└────────────────┬─────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────┐
│         Application Service                  │
│    Spring Boot + Spring AI + LangChain4j     │
└──┬─────────┬─────────┬─────────┬─────────┬──┘
   │         │         │         │         │
┌──┴──┐  ┌──┴──┐  ┌──┴──┐  ┌──┴──┐  ┌──┴──┐
│MySQL│  │Mongo│  │Redis│ │Qdrant│ │Ollama│
└─────┘  └─────┘  └─────┘  └─────┘  └─────┘
```

### 数据流
1. 用户请求 → 应用服务 → 数据库查询
2. 长文本 → RAG 向量检索 → 增强翻译
3. 文档上传 → 向量化 → Qdrant 存储
4. 聊天消息 → MCP 协议 → 工具调用 → 响应

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'Add amazing feature'`
4. 推送分支：`git push origin feature/amazing-feature`
5. 提交 Pull Request

## 📝 许可证

请查看项目根目录下的 LICENSE 文件了解许可证详情。

## 📞 支持

遇到问题请：
1. 首先查看 `DOCKER_DESKTOP_GUIDE.md` 中的故障排查部分
2. 检查应用日志：`./manage-desktop.sh logs`
3. 运行健康检查：`./manage-desktop.sh health`
4. 提交 Issue 获取帮助

---

**Happy Translating! 🚀**