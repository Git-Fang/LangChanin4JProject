# JDBC URL 配置错误修复指南

## 问题描述

应用启动时出现以下错误：

```
java.sql.SQLException: Unsupported character encoding 'UTF-8;serverTimezone=Asia/Shanghai;useSSL=false;allowPublicKeyRetrieval=true'
```

## 根本原因

JDBC URL 中的参数使用了错误的分隔符 `;`，正确的分隔符应该是 `&`。

### 错误示例
```
jdbc:mysql://mysql:3306/mydocker?characterEncoding=UTF-8;serverTimezone=Asia/Shanghai;useSSL=false;allowPublicKeyRetrieval=true
```

### 正确示例
```
jdbc:mysql://mysql:3306/mydocker?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

## 修复方案

### 方案 1：代码自动修复（已实现）

修改了 `DatabaseConfig.java`，在创建 DataSource 前自动修复 URL：
- 检测 URL 中的 `;` 分隔符
- 自动替换为 `&`
- 记录日志提示修复操作

### 方案 2：配置验证器（已实现）

添加了 `DataSourceConfigValidator.java`：
- 在应用启动时验证配置
- 如果检测到错误的分隔符，抛出异常阻止启动
- 提供清晰的错误信息和修复建议

### 方案 3：Docker 入口点脚本（已实现）

更新了 `docker-entrypoint.sh` 和 `Dockerfile`：
- 在容器启动时检查环境变量
- 自动修复 `SPRING_DATASOURCE_URL` 中的分隔符
- 保留原有 JVM 参数配置

## 配置检查清单

### 本地开发环境
检查 `application-docker.yml` 第 35 行：
```yaml
url: jdbc:mysql://mysql:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

### Nacos 配置中心
检查配置 `RAGTranslationApplication-docker.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

### Docker 环境变量
检查 `.env` 文件或 docker-compose 中的环境变量：
```bash
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

## 验证修复

修复后，应用启动日志应显示：
```
=== 数据源配置验证开始 ===
数据源 URL: jdbc:mysql://mysql:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
数据源用户名: root
=== 数据源配置验证完成 ===

HikariPool-1 - Starting...
HikariPool-1 - Start completed  # 关键：显示 Start completed 表示成功
```

如果看到以下日志，表示修复生效：
```
⚠️  检测到 JDBC URL 使用了错误的参数分隔符 ';'，已自动修复为 '&'
原始 URL: jdbc:mysql://...?characterEncoding=UTF-8;serverTimezone=...
修复后 URL: jdbc:mysql://...?characterEncoding=UTF-8&serverTimezone=...
```

## 常见问题

### Q: 为什么配置文件中的 URL 是正确的，但运行时出错？
A: 可能的原因：
1. Nacos 配置中心覆盖了本地配置
2. Docker 环境变量 `SPRING_DATASOURCE_URL` 传入了错误的 URL
3. 配置文件的编码问题导致特殊字符被错误解析

### Q: 如何确认当前使用的配置来源？
A: 查看应用启动日志中的 `DataSourceConfigValidator` 输出，会打印实际使用的 URL。

### Q: 修复后仍然报错？
A: 请检查：
1. MySQL 服务是否正常运行
2. 数据库 `mydocker` 是否存在
3. 用户名密码是否正确
4. 网络连接是否正常（Docker 容器能否访问 MySQL）
