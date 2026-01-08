# Docker部署指南

## 快速部署步骤

### 1. 准备工作

确保Ubuntu虚拟机（192.168.179.130）已安装：
- Docker
- Docker Compose

如未安装，执行：
```bash
# 安装Docker
curl -fsSL https://get.docker.com | sh

# 启动Docker服务
sudo systemctl start docker
sudo systemctl enable docker

# 安装Docker Compose
sudo apt-get update
sudo apt-get install -y docker-compose

# 将当前用户添加到docker组（可选，避免每次使用sudo）
sudo usermod -aG docker $USER
# 重新登录生效
```

### 2. 上传项目文件到虚拟机

```bash
# 从Windows上传到Ubuntu虚拟机
# 方法1：使用scp
scp -r "d:\个人资料\AI实战--RAG增强型翻译\RAGTranslation-- mcp--docker" user@192.168.179.130:/home/user/

# 方法2：使用git
# 在虚拟机上克隆项目
```

### 3. 配置环境变量

确保项目根目录下有`.env`文件，包含以下配置：

```env
# API密钥配置
DeepSeek_API_KEY=your_key_here
KIMI_API_KEY=your_key_here
DASHSCOPE_API_KEY=your_key_here
BAIDU_MAP_API_KEY=your_key_here

# 服务配置
SERVER_PORT=8000
```

### 4. 执行部署

```bash
# 进入项目目录
cd RAGTranslation-- mcp--docker

# 赋予部署脚本执行权限
chmod +x deploy.sh

# 执行部署
./deploy.sh
```

### 5. 验证部署

部署成功后访问：
- 主页面：http://192.168.179.130:8000/index.html
- API文档：http://192.168.179.130:8000/doc.html

## 常用命令

```bash
# 查看服务状态
docker-compose ps

# 查看实时日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f rag-translation

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 重新构建并启动
docker-compose up -d --build
```

## 优化说明

本次改造的优化点：

### 1. 镜像优化
- **构建阶段**：使用`maven:3.9-eclipse-temurin-17-alpine`替代Ubuntu+手动安装
  - 减少了镜像层数
  - 利用官方镜像的优化配置
  - Alpine基础镜像更小（约150MB vs 400MB+）

- **运行阶段**：使用`eclipse-temurin:17-jre-alpine`
  - JRE代替JDK，减少约100MB
  - Alpine基础镜像进一步减小体积

### 2. 构建速度优化
- 简化了Maven settings.xml的配置过程
- 保留了依赖缓存机制（`dependency:go-offline`）
- 利用Docker层缓存，依赖不变时不重新下载

### 3. 配置优化
- 保持了阿里云镜像加速配置
- 保留了多阶段构建，分离构建和运行环境
- 端口和网络配置保持不变

## 预期效果

- **镜像大小**：从 ~800MB 减少到 ~400MB
- **构建时间**：首次构建时间相近，二次构建（依赖已缓存）速度提升约40%
- **运行资源**：内存占用减少约20%

## 故障排查

### 问题1：无法访问服务

```bash
# 检查容器是否运行
docker-compose ps

# 检查端口是否监听
netstat -tunlp | grep 8000

# 检查防火墙
sudo ufw status
sudo ufw allow 8000
```

### 问题2：构建失败

```bash
# 清理Docker缓存
docker system prune -a

# 重新构建
docker-compose build --no-cache
```

### 问题3：依赖下载慢

确保Dockerfile中的阿里云镜像配置正确，或临时使用代理：

```bash
# 设置代理（如果需要）
export HTTP_PROXY=http://proxy:port
export HTTPS_PROXY=http://proxy:port
docker-compose build
```
