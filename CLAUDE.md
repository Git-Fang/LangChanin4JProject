# CLAUDE.md

该文件为Claude Code (claude.ai/code)在此仓库中处理代码时提供指导。

常用开发命令
构建命令
bash
# 构建项目
./mvnw clean package

# 本地运行应用
./mvnw spring-boot:run

# 构建Docker镜像
docker-compose build

# 使用Docker部署
./deploy.sh
# 或使用启动脚本
./start.sh
开发工作流
bash
# 查看日志
docker-compose logs -f rag-translation
docker-compose logs -f mongo

# 重启服务
docker-compose restart

# 停止所有服务
docker-compose down

# 访问应用
# Web界面: http://localhost:8000/index.html
# API文档: http://localhost:8000/doc.html
诊断命令
bash
# 检查部署状态
./diagnose.sh

# 检查应用日志
./check-logs.sh

# 修复Docker超时问题
./fix-docker-timeout.sh
高层次架构
核心组件
多模态翻译系统

支持多种AI增强翻译模式 (src/main/java/org/fb/controller/TranslationController.java)

集成多个LLM (DeepSeek, Kimi, DashScope, Ollama)

使用Qdrant向量数据库进行上下文检索的RAG实现

MCP (多上下文协议) 实现

工具调用能力 (src/main/java/org/fb/controller/McpChatController.java)

SSE (服务器发送事件) 流式传输 (src/main/java/org/fb/controller/McpSseController.java)

与MCP工具集成的聊天功能 (src/main/java/org/fb/service/impl/ChatServiceImpl.java)

数据存储架构

MySQL: 应用数据和翻译记录

MongoDB: 聊天历史和会话上下文

Qdrant: RAG检索的向量存储

配置: application.yml管理所有数据库连接

AI模型集成

Spring AI 1.0.0用于LLM编排

LangChain4j 1.5.0用于AI工作流和RAG实现

嵌入模型: All-MiniLM-L6-v2模型用于文本向量化

多提供商支持，基于环境变量配置API密钥

请求流程
翻译请求

用户输入 → TranslationController → TranslaterService

可能使用RAG从Qdrant检索上下文

AI模型使用增强提示进行处理

返回响应，可选流式传输

使用MCP工具的聊天

聊天请求 → ChatController → ChatServiceImpl

通过MCP协议执行工具(如需要)

结果集成到响应中

会话存储在MongoDB中

文档处理

文档上传 → DocumentController → DocumentService

文档内容向量化并存储在Qdrant中

可用于基于RAG的查询

关键配置
环境变量: .env文件中的API密钥和配置

Docker服务: 通过docker-compose.yml编排MongoDB和应用

应用端口: 8000 (可通过SERVER_PORT环境变量配置)

数据库连接: 在application.yml中配置，支持环境变量

开发注意事项
应用使用Java 17和Spring Boot 3.5.0

多阶段Docker构建减少最终镜像大小

为AI组件配置了详细的日志记录(调试级别)

当前未实现单元测试

前端静态文件从src/main/resources/static/提供