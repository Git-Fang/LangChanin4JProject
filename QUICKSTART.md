# Docker部署快速参考

## 在Ubuntu虚拟机上部署（192.168.179.130）

### 一键部署
```bash
chmod +x deploy.sh && ./deploy.sh
```

### 或者使用原脚本
```bash
chmod +x start.sh && ./start.sh
```

### 手动部署步骤
```bash
# 1. 构建镜像
docker-compose build

# 2. 停止旧容器
docker-compose down

# 3. 启动服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f
```

## 访问地址
- 主页：http://192.168.179.130:8000/index.html
- API文档：http://192.168.179.130:8000/doc.html

## 常用命令
```bash
# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 重新构建
docker-compose up -d --build
```

## 改造说明

### 优化点
1. **镜像更轻量**：
   - 构建阶段：maven:3.9-eclipse-temurin-17-alpine (~350MB)
   - 运行阶段：eclipse-temurin:17-jre-alpine (~170MB)
   - 最终镜像：~400MB (原约800MB)

2. **构建更快速**：
   - 简化了Maven配置生成
   - 保留依赖缓存机制
   - 利用Docker层缓存

3. **改动最小化**：
   - 仅修改Dockerfile基础镜像
   - docker-compose.yml保持不变
   - 应用配置保持不变

### 文件修改清单
- ✅ Dockerfile - 优化基础镜像和构建过程
- ✅ start.sh - 移除--no-cache参数，加快二次构建
- ✅ deploy.sh - 新增部署脚本
- ✅ DEPLOY.md - 详细部署文档
- ✅ QUICKSTART.md - 快速参考（本文件）
- ⚪ docker-compose.yml - 未修改
- ⚪ .env - 未修改
- ⚪ application.yml - 未修改

## 故障排查

### 无法访问
```bash
# 检查容器状态
docker-compose ps

# 检查日志错误
docker-compose logs rag-translation

# 检查端口
sudo netstat -tunlp | grep 8000

# 开放防火墙
sudo ufw allow 8000/tcp
```

### 构建失败
```bash
# 清理并重建
docker-compose down
docker system prune -f
docker-compose build --no-cache
```
