# Nacos配置中心适配说明

## 概述

本项目已适配Nacos作为配置中心和服务注册中心，支持：
- 配置的集中管理和动态更新
- 服务的自动注册与发现
- 多环境配置隔离

## 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `Dockerfile` | 将profile从`docker-bypass`改为`docker`，启用Nacos |
| `src/main/resources/application-docker.yml` | 使用spring.config.import方式导入Nacos配置 |
| `src/main/resources/bootstrap.yml` | 新建，配置Nacos Config引导 |
| `src/main/resources/static/index.html` | 页面API地址动态获取 |

## 使用方法

### 1. 启动Nacos

确保Nacos服务已启动（deploy-desktop.bat会自动启动）：

```bash
# Nacos控制台: http://localhost:8848/nacos
# 默认用户名: nacos
# 默认密码: nacos
```

### 2. 初始化Nacos配置

运行配置初始化脚本：

```bash
nacos-config-init.bat
```

或者手动在Nacos控制台创建以下配置：

#### Docker环境配置
- **DataId**: `RAGTranslationApplication-docker.yml`
- **Group**: `DEFAULT_GROUP`
- **类型**: YAML

#### Standalone环境配置
- **DataId**: `RAGTranslationApplication-standalone.yml`
- **Group**: `DEFAULT_GROUP`
- **类型**: YAML

### 3. 启动应用

#### Docker部署
```bash
deploy-desktop.bat
```

#### IDEA本地启动
```bash
# 使用standalone profile
mvn spring-boot:run -Dspring.profiles.active=standalone

# 或使用docker profile（连接Docker中的Nacos）
mvn spring-boot:run -Dspring.profiles.active=docker -DNACOS_SERVER_ADDR=localhost:8848
```

## 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `NACOS_SERVER_ADDR` | Nacos服务器地址 | `nacos:8848` (docker) / `localhost:8848` (standalone) |
| `NACOS_NAMESPACE` | 命名空间ID | 空 |
| `SPRING_PROFILES_ACTIVE` | 激活的profile | `docker` (docker) / `standalone` (本地) |

### 配置热更新

修改Nacos中的配置后，应用程序会自动感知变化并更新配置，无需重启服务。

### 服务注册

应用启动后会自动注册到Nacos服务注册中心，可在Nacos控制台查看：
```
http://localhost:8848/nacos/#/serviceManagement
```

## 注意事项

1. **本地开发**：使用`standalone` profile连接本地Nacos
2. **Docker部署**：使用`docker` profile连接Docker网络中的Nacos
3. **配置优先级**：Nacos配置 > application.yml配置 > 硬编码默认值
4. **兼容性**：已确保Spring Boot 3.x + Spring Cloud Alibaba 2023.x的兼容性

## 常见问题

### Q: 启动时连接Nacos失败？
A: 检查Nacos服务是否启动，以及网络连接是否正常。Docker环境中确保容器在同一网络。

### Q: 配置不生效？
A: 检查DataId和Group是否正确，确保配置文件格式为有效的YAML。

### Q: 如何回退到本地配置？
A: 不启动Nacos或设置错误的Nacos地址，应用会使用本地配置文件启动。

## 监控

- **Nacos控制台**: http://localhost:8848/nacos
- **应用健康检查**: http://localhost:8000/actuator/health
