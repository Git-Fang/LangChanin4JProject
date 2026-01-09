# Docker镜像拉取问题解决方案

## 问题描述
Docker构建时无法拉取 `eclipse-temurin:17-jre-alpine` 和 `maven:3.9-eclipse-temurin-17-alpine` 镜像，报错：`failed to resolve source metadata`。

## 解决方案

### 方案1：配置Docker镜像加速器（推荐）

1. **打开Docker Desktop设置**：
   - 右键点击系统托盘中的Docker图标
   - 选择 **Settings**

2. **配置镜像加速器**：
   - 选择 **Docker Engine** 选项卡
   - 在JSON配置中添加以下内容：
   ```json
   {
     "registry-mirrors": [
       "https://hub-mirror.c.163.com",
       "https://mirror.ccs.tencentyun.com",
       "https://阿里云镜像加速器地址.mirror.aliyuncs.com"
     ]
   }
   ```

3. **应用并重启**：
   - 点击 **Apply & Restart**
   - 等待Docker重启完成

4. **运行修复脚本**：
   ```bash
   fix-docker-issue.bat
   ```

### 方案2：手动拉取基础镜像

如果配置了加速器后仍有问题，可以尝试手动拉取基础镜像：

```bash
# 拉取Alpine基础镜像
docker pull alpine:3.18

# 拉取OpenJDK镜像
docker pull openjdk:17-jdk-alpine
```

然后使用本地Dockerfile构建：
```bash
docker build -f Dockerfile.local -t rag-translation:latest .
```

### 方案3：使用已有的镜像

1. **修改docker-compose.yml**：
   注释掉 `build: .` 行，取消 `image: rag-translation:latest` 的注释

2. **构建镜像**：
   ```bash
   docker build -f Dockerfile.local -t rag-translation:latest .
   ```

3. **启动服务**：
   ```bash
   docker-compose up -d
   ```

### 方案4：使用Portainer等可视化工具

如果命令行方式持续失败，可以使用Portainer等Docker管理工具的Web界面来管理镜像和容器。

## 常用国内镜像加速器地址

- 网易云：`https://hub-mirror.c.163.com`
- 腾讯云：`https://mirror.ccs.tencentyun.com`
- 阿里云：`https://阿里云镜像加速器地址.mirror.aliyuncs.com`
- 中国科技大学：`https://docker.mirrors.ustc.edu.cn`

## 验证是否修复

运行以下命令验证Docker是否可以正常拉取镜像：

```bash
docker run --rm hello-world
```

如果看到欢迎信息，说明Docker网络连接已恢复正常。

## 后续步骤

修复镜像拉取问题后，可以正常运行：

```bash
# 重新构建并部署
./deploy-desktop.bat

# 或者直接使用Docker Compose
docker-compose up -d
```

如果仍有问题，请检查：
- 网络连接是否正常
- 防火墙或代理设置
- Docker Desktop版本是否为最新