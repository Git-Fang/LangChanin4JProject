# RAG增强型翻译系统

## 项目简介

RAG增强型翻译系统是一个基于Spring Boot和大模型的翻译应用，支持多种翻译模式和智能交互。

## 项目结构

```
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/fb/
│   │   │       ├── bean/          # 实体类
│   │   │       ├── config/       # 配置类
│   │   │       ├── constant/     # 常量类
│   │   │       ├── controller/   # 控制器
│   │   │       ├── mcp/          # MCP相关代码
│   │   │       ├── service/      # 服务层
│   │   │       └── tools/        # 工具类
│   │   └── resources/            # 资源文件
│   │       ├── mapper/           # MyBatis映射文件
│   │       ├── static/           # 静态资源
│   │       ├── application.yml   # 配置文件
│   │       └── *.txt             # 提示词模板
│   └── test/                     # 测试代码
├── Dockerfile                    # Docker构建文件
├── docker-compose.yml            # Docker Compose配置
├── pom.xml                       # Maven配置
└── README.md                     # 项目说明
```

## 环境要求

- Java 17
- Maven 3.6+
- Docker (可选，用于容器部署)
- Docker Compose (可选，用于容器编排)

## 环境变量配置

| 环境变量名 | 描述 | 示例值 |
| --- | --- | --- |
| DeepSeek_API_KEY | DeepSeek API密钥 | sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx |
| KIMI_API_KEY | Kimi API密钥 | sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx |
| DASHSCOPE_API_KEY | 阿里云DashScope API密钥 | sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx |
| BAIDU_MAP_API_KEY | 百度地图API密钥 | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx |

## 部署步骤

### 1. 克隆项目

```bash
git clone <repository-url>
cd RAGTranslation--mcp--docker
```

### 2. 配置环境变量

创建`.env`文件，配置所需的环境变量：

```dotenv
DeepSeek_API_KEY=your_deepseek_api_key
KIMI_API_KEY=your_kimi_api_key
DASHSCOPE_API_KEY=your_dashscope_api_key
BAIDU_MAP_API_KEY=your_baidu_map_api_key
```

### 3. 构建和运行

#### 方式一：使用Maven直接运行

```bash
./mvnw spring-boot:run
```

#### 方式二：使用Docker Compose部署

```bash
docker-compose up -d
```

## 使用说明

### 访问应用

应用启动后，可以通过以下地址访问：

- Web界面：http://172.21.192.1:8000
- API文档：http://172.21.192.1:8000/doc.html

### 主要功能

1. **翻译功能**：支持多种翻译模式和语言
2. **智能交互**：基于大模型的智能对话
3. **历史记录**：保存和管理对话历史
4. **文件上传**：支持上传文件进行翻译
5. **MCP服务**：支持MCP协议的工具调用

## API文档

应用集成了Knife4j，提供了完整的API文档，可以通过以下地址访问：

http://172.21.192.1:8000/doc.html

## 技术栈

- **后端框架**：Spring Boot 3.5.0
- **ORM框架**：MyBatis Plus 3.5.11
- **数据库**：MySQL、MongoDB
- **大模型集成**：Spring AI 1.0.0
- **向量数据库**：Qdrant
- **前端**：HTML、JavaScript
- **构建工具**：Maven
- **容器化**：Docker、Docker Compose

## 开发说明

### 代码结构

- **controller**：处理HTTP请求，返回响应
- **service**：业务逻辑层，实现核心功能
- **dao/mapper**：数据访问层，操作数据库
- **bean**：实体类，映射数据库表或业务对象
- **config**：配置类，管理应用配置
- **constant**：常量类，定义应用常量
- **tools**：工具类，提供通用功能

### 构建命令

```bash
# 编译代码
./mvnw compile

# 运行测试
./mvnw test

# 打包应用
./mvnw package

# 清理构建产物
./mvnw clean
```

## 注意事项

1. 确保所有依赖服务（MySQL、MongoDB、Qdrant等）正常运行
2. 配置正确的API密钥和连接地址
3. 容器部署时，确保端口映射正确
4. 首次启动时，会自动创建数据库表结构

## 许可证

MIT