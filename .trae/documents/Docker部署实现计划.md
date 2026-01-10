# Docker部署实现计划

## 一、需求分析
1. **外部访问URL**：http://localhost:8000/unified.html
2. **指定镜像**：mysql:8.0, redis/redis-stack:latest, rabbitmq:3-management, mongo:7.0, qdrant/qdrant:latest
3. **不使用ollama**：减少耗时
4. **启动入口**：deploy-desktop.bat
5. **支持Docker Desktop部署**：将项目打包成镜像
6. **精简文件**：保留必要文件，删除未使用的Docker相关文件

## 二、现有文件分析
### 保留文件
1. `docker-compose.yml`：已使用指定镜像，配置完整
2. `Dockerfile.optimize`：优化的构建文件，不包含ollama相关内容
3. `deploy-desktop.bat`：现有启动脚本，功能完整
4. `health-check.bat`：用于检查服务状态

### 删除文件
- `Dockerfile`
- `Dockerfile-simple`
- `Dockerfile.final`
- `Dockerfile.skip-ai`
- `Dockerfile.test`
- `docker-compose-local.yml`
- `docker-compose-mock.yml`
- `docker-compose-standalone.yml`
- `deploy-desktop-quick.bat`
- `deploy-now.bat`

## 三、实现步骤
1. **删除冗余文件**：清理未使用的Docker相关文件
2. **优化deploy-desktop.bat**：确保脚本逻辑正确，支持本地镜像检查
3. **验证docker-compose.yml**：确认使用指定镜像，配置正确
4. **验证Dockerfile.optimize**：确认不包含ollama相关内容
5. **测试部署流程**：确保部署脚本能正常工作

## 四、预期结果
1. 项目包含最少必要的Docker相关文件
2. 通过deploy-desktop.bat可以成功部署到Docker Desktop
3. 外部可以通过http://localhost:8000/unified.html访问
4. 所有服务使用指定镜像
5. 不包含ollama相关镜像操作

## 五、文件变更清单
- **保留**：docker-compose.yml, Dockerfile.optimize, deploy-desktop.bat, health-check.bat
- **删除**：其余未使用的Docker相关文件
- **修改**：无（现有文件已符合要求）