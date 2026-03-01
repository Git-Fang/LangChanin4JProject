# Prometheus+Grafana 监控系统全面部署与配置指南

## 1. 监控系统架构

### 1.1 架构概述

本项目采用 Prometheus + Grafana 作为监控解决方案，整体架构如下：

```
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│                 │      │                 │      │                 │
│  应用程序 (Spring Boot)  │ ────> │   Prometheus   │ ────> │   Grafana      │
│                 │      │                 │      │                 │
└─────────────────┘      └─────────────────┘      └─────────────────┘
        ↑                        ↑                        ↑
        │                        │                        │
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│                 │      │                 │      │                 │
│   MongoDB       │ ────> │                 │      │                 │
│                 │      │                 │      │                 │
└─────────────────┘      │                 │      │                 │
        ↑                │                 │      │                 │
        │                │                 │      │                 │
┌─────────────────┐      │                 │      │                 │
│                 │      │                 │      │                 │
│    Redis        │ ────> │                 │      │                 │
│                 │      │                 │      │                 │
└─────────────────┘      │                 │      │                 │
        ↑                │                 │      │                 │
        │                │                 │      │                 │
┌─────────────────┐      │                 │      │                 │
│                 │      │                 │      │                 │
│    Qdrant       │ ────> │                 │      │                 │
│                 │      │                 │      │                 │
└─────────────────┘      └─────────────────┘      └─────────────────┘
```

### 1.2 组件说明

| 组件 | 版本 | 功能 | 端口 |
|------|------|------|------|
| Prometheus | v2.47.0 | 指标收集与存储 | 9090 |
| Grafana | 10.1.10 | 指标可视化与告警 | 3000 |
| Spring Boot Actuator | 3.5.0 | 应用指标暴露 | 8000 (应用端口) |
| MongoDB Exporter | - | MongoDB指标暴露 | 9216 |
| Redis Exporter | - | Redis指标暴露 | 9121 |
| Qdrant | v1.12.0 | 向量数据库 (自带指标端点) | 6333 |

## 2. 环境准备

### 2.1 前置条件

- Docker Desktop 已安装并运行
- 项目代码已克隆到本地
- Maven 已安装 (用于构建项目)

### 2.2 启动监控服务

1. **启动 Docker 网络**：
   ```powershell
   docker network create ai-network
   ```

2. **启动监控服务**：
   ```powershell
   docker-compose -f docker-compose-monitoring.yml up -d
   ```

3. **验证服务状态**：
   ```powershell
   docker ps | findstr "prometheus grafana"
   ```

## 3. 配置步骤

### 3.1 应用程序配置

1. **添加依赖** (pom.xml 已配置)：
   ```xml
   <!-- Spring Boot Actuator for Prometheus metrics -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   <!-- Apache Commons Pool2 for Redis connection pooling -->
   <dependency>
       <groupId>org.apache.commons</groupId>
       <artifactId>commons-pool2</artifactId>
   </dependency>
   ```

2. **配置 Actuator** (application-docker.yml 已配置)：
   ```yaml
   # Actuator配置
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus
     endpoint:
       health:
         show-details: always
       prometheus:
         enabled: true
     metrics:
       tags:
         application: ${spring.application.name}
   ```

3. **监控配置类**：
   - `MonitoringConfig.java` - 配置JVM相关指标
   - `RedisMonitoringConfig.java` - 配置Redis监控指标
   - `MongoMonitoringConfig.java` - 配置MongoDB监控指标

### 3.2 Prometheus 配置

1. **编辑 prometheus.yml**：
   ```yaml
   global:
     scrape_interval: 15s  # 抓取间隔
     evaluation_interval: 15s  # 评估间隔

   # 抓取配置
   scrape_configs:
     # 抓取Prometheus自身的指标
     - job_name: 'prometheus'
       static_configs:
         - targets: ['localhost:9090']

     # 抓取应用程序的指标
     - job_name: 'rag-translation'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8000']

     # 抓取MongoDB的指标
     - job_name: 'mongodb'
       static_configs:
         - targets: ['mongodb-exporter:9216']
       metrics_path: /metrics
       scrape_interval: 15s
       scrape_timeout: 10s

     # 抓取Redis的指标
     - job_name: 'redis'
       static_configs:
         - targets: ['redis-exporter:9121']
       metrics_path: /metrics
       scrape_interval: 15s
       scrape_timeout: 10s

     # 抓取Qdrant的指标
     - job_name: 'qdrant'
       static_configs:
         - targets: ['qdrant:6333']
   ```

2. **重启 Prometheus**：
   ```powershell
   docker restart prometheus
   ```

### 3.3 Grafana 配置

1. **访问 Grafana**：
   - URL: `http://localhost:3000`
   - 默认登录凭据: `admin` / `admin123`

2. **添加 Prometheus 数据源**：
   - 点击左侧菜单 "Configuration" > "Data sources"
   - 点击 "Add data source"
   - 选择 "Prometheus"
   - URL: `http://prometheus:9090`
   - 点击 "Save & Test"

## 4. 重要指标监控

### 4.1 应用程序指标

| 指标名称 | 描述 | 单位 | 告警阈值 |
|---------|------|------|---------|
| `http_server_requests_seconds_count` | HTTP请求计数 | 次 | - |
| `http_server_requests_seconds_sum` | HTTP请求总响应时间 | 秒 | - |
| `http_server_requests_seconds_max` | HTTP请求最大响应时间 | 秒 | >5 |
| `jvm_memory_used_bytes` | JVM已使用内存 | 字节 | >80% 堆内存 |
| `jvm_memory_max_bytes` | JVM最大内存 | 字节 | - |
| `jvm_threads_live_threads` | 活跃线程数 | 个 | - |
| `jvm_threads_daemon_threads` | 守护线程数 | 个 | - |
| `process_cpu_usage` | CPU使用率 | 百分比 | >80% |
| `system_cpu_usage` | 系统CPU使用率 | 百分比 | >90% |

### 4.2 MongoDB 指标

| 指标名称 | 描述 | 单位 | 告警阈值 |
|---------|------|------|---------|
| `mongodb_connections` | MongoDB连接数 | 个 | >500 |
| `mongodb_commands_total` | MongoDB命令执行总数 | 次 | - |
| `mongodb_op_latencies_seconds` | MongoDB操作延迟 | 秒 | >0.5 |

### 4.3 Redis 指标

| 指标名称 | 描述 | 单位 | 告警阈值 |
|---------|------|------|---------|
| `redis_connected_clients` | Redis连接客户端数 | 个 | >1000 |
| `redis_used_memory_bytes` | Redis已使用内存 | 字节 | >80% 最大内存 |
| `redis_keyspace_hits_total` | Redis键命中数 | 次 | - |
| `redis_keyspace_misses_total` | Redis键未命中数 | 次 | - |
| `redis_commands_processed_total` | Redis命令处理总数 | 次 | - |

### 4.4 Qdrant 指标

| 指标名称 | 描述 | 单位 | 告警阈值 |
|---------|------|------|---------|
| `qdrant_collection_points_count` | Qdrant集合点数 | 个 | - |
| `qdrant_requests_total` | Qdrant请求总数 | 次 | - |
| `qdrant_request_duration_seconds` | Qdrant请求延迟 | 秒 | >1 |

## 5. 告警配置

### 5.1 Prometheus 告警规则

1. **创建告警规则文件** `alerting_rules.yml`：
   ```yaml
   groups:
   - name: application_alerts
     rules:
     - alert: HighHttpRequestLatency
       expr: http_server_requests_seconds_max > 5
       for: 5m
       labels:
         severity: warning
       annotations:
         summary: "高HTTP请求延迟"
         description: "应用程序HTTP请求最大延迟超过5秒"

     - alert: HighJvmMemoryUsage
       expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100 > 80
       for: 5m
       labels:
         severity: warning
       annotations:
         summary: "高JVM内存使用率"
         description: "JVM堆内存使用率超过80%"

     - alert: HighCpuUsage
       expr: process_cpu_usage > 0.8
       for: 5m
       labels:
         severity: warning
       annotations:
         summary: "高CPU使用率"
         description: "应用程序CPU使用率超过80%"
   ```

2. **更新 prometheus.yml** 添加告警规则：
   ```yaml
   rule_files:
     - /etc/prometheus/alerting_rules.yml
   ```

### 5.2 Grafana 告警通道

1. **添加告警通道**：
   - 点击左侧菜单 "Alerting" > "Notification channels"
   - 点击 "Add channel"
   - 选择通道类型 (如 Email、Slack、Webhook 等)
   - 配置通道参数
   - 点击 "Save"

## 6. 可视化面板

### 6.1 导入预制面板

1. **导入 JVM 面板**：
   - 点击左侧菜单 "Dashboards" > "Import"
   - 输入面板 ID: `4701` (Spring Boot 2.1 + JVM (Micrometer))
   - 选择 Prometheus 数据源
   - 点击 "Import"

或者使用提供的仪表板JSON文件：
- `dashboard-configs/jvm-dashboard.json`

2. **导入 MongoDB 面板**：
   - 点击左侧菜单 "Dashboards" > "Import"
   - 输入面板 ID: `7353` (MongoDB Overview)
   - 选择 Prometheus 数据源
   - 点击 "Import"

或者使用提供的仪表板JSON文件：
- `dashboard-configs/mongodb-dashboard.json`

3. **导入 Redis 面板**：
   - 点击左侧菜单 "Dashboards" > "Import"
   - 输入面板 ID: `763` (Redis Dashboard)
   - 选择 Prometheus 数据源
   - 点击 "Import"

或者使用提供的仪表板JSON文件：
- `dashboard-configs/redis-dashboard.json`

### 6.2 自定义面板

您可以使用Grafana的仪表板编辑器创建自定义仪表板，使用以下常用查询：

### JVM相关查询
- `jvm_memory_used_bytes{application="RAGTranslationApplication"}`
- `process_cpu_usage{application="RAGTranslationApplication"}`
- `jvm_gc_pause_seconds_count{application="RAGTranslationApplication"}`

### Redis相关查询
- `redis_memory_used_bytes{instance="redis:6379"}`
- `redis_connected_clients{instance="redis:6379"}`
- `rate(redis_commands_processed_total{instance="redis:6379"}[5m])`

### MongoDB相关查询
- `mongodb_connections_current{instance="mongodb-exporter:9216"}`
- `rate(mongodb_opcounters_total{instance="mongodb-exporter:9216"}[5m])`
- `mongodb_memory_resident{instance="mongodb-exporter:9216"}`

## 7. 最佳实践

### 7.1 监控最佳实践

1. **指标命名规范**：
   - 使用 snake_case 命名
   - 包含清晰的指标描述
   - 使用标签区分不同维度

2. **抓取间隔设置**：
   - 常规指标：15s
   - 资源密集型指标：30s-60s
   - 低频变更指标：5m

3. **存储配置**：
   - 配置适当的存储保留时间
   - 考虑使用远程存储方案 (如 Thanos、VictoriaMetrics)

4. **安全配置**：
   - 配置 Grafana 访问控制
   - 限制 Prometheus 访问
   - 使用 HTTPS 加密传输

### 7.2 故障排查指南

1. **高 CPU 使用率**：
   - 检查应用程序线程状态
   - 分析 HTTP 请求量
   - 查看数据库查询性能

2. **高内存使用率**：
   - 检查 JVM 堆内存配置
   - 分析内存泄漏
   - 查看缓存使用情况

3. **HTTP 请求延迟高**：
   - 分析请求处理时间分布
   - 检查数据库查询性能
   - 查看外部服务调用延迟

4. **数据库连接问题**：
   - 检查连接池配置
   - 分析慢查询
   - 查看数据库服务器状态

## 8. 部署与维护

### 8.1 部署步骤

1. **构建应用程序**：
   ```powershell
   mvn clean package -DskipTests
   ```

2. **构建 Docker 镜像**：
   ```powershell
   docker build -t ragtranslation-app:latest .
   ```

3. **启动所有服务**：
   ```powershell
   .\deploy-desktop.bat
   ```

### 8.2 维护任务

1. **定期备份**：
   - 备份 Grafana 配置和面板
   - 备份 Prometheus 告警规则

2. **监控系统本身**：
   - 监控 Prometheus 存储使用情况
   - 监控 Grafana 服务状态

3. **版本升级**：
   - 定期升级 Prometheus 和 Grafana 版本
   - 测试新版本兼容性

## 9. 常见问题与解决方案

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| Prometheus 无法抓取应用指标 | 网络连接问题 | 检查容器网络配置，确保在同一网络中 |
| Grafana 面板无数据 | 数据源配置错误 | 检查 Prometheus 数据源 URL 配置 |
| 应用程序启动失败 | 端口冲突 | 检查端口占用情况，修改配置文件 |
| 告警未触发 | 告警规则配置错误 | 检查告警规则表达式，使用 Prometheus 表达式浏览器测试 |
| 监控数据丢失 | 存储配置问题 | 配置适当的存储保留时间，考虑使用远程存储 |
| 应用程序循环依赖错误 | Bean定义冲突 | 检查并修复配置类中的依赖注入问题 |
| Redis连接池错误 | 缺少commons-pool2依赖 | 添加commons-pool2依赖到项目中 |

## 10. 项目修改细节总结

### 10.1 新增配置文件
- `src/main/java/org/fb/config/MonitoringConfig.java` - JVM指标监控配置
- `src/main/java/org/fb/config/RedisMonitoringConfig.java` - Redis监控配置
- `src/main/java/org/fb/config/MongoMonitoringConfig.java` - MongoDB监控配置
- `dashboard-configs/jvm-dashboard.json` - JVM监控仪表板配置
- `dashboard-configs/redis-dashboard.json` - Redis监控仪表板配置
- `dashboard-configs/mongodb-dashboard.json` - MongoDB监控仪表板配置

### 10.2 修改的配置文件
- `pom.xml` - 添加了commons-pool2依赖以支持Redis连接池
- `prometheus.yml` - 更新了抓取配置以适配容器网络
- `src/main/java/org/fb/config/MongoConfig.java` - 优化MongoDB配置
- `src/main/java/org/fb/config/RedisConfig.java` - 添加Redis监控配置

### 10.3 修复的问题
1. 解决了Bean定义冲突问题
2. 解决了循环依赖问题
3. 解决了Redis连接池依赖缺失问题
4. 解决了容器网络连接问题
5. 优化了监控配置类

## 11. 总结

本监控方案基于 Prometheus + Grafana 构建，提供了全面的指标监控能力，包括：

- **应用程序监控**：HTTP 请求、JVM 状态、系统资源
- **数据库监控**：MongoDB、Redis、Qdrant
- **可视化面板**：预制面板 + 自定义面板
- **告警系统**：基于 Prometheus 告警规则

通过这套监控系统，您可以：

1. **实时了解系统状态**：通过 Grafana 面板直观查看各项指标
2. **及时发现问题**：通过告警系统提前发现潜在问题
3. **优化系统性能**：通过分析监控数据，找出性能瓶颈
4. **确保系统稳定**：及时响应异常情况，保障系统正常运行

对于新手来说，建议从基础监控开始，逐步添加高级功能，如自定义指标、复杂告警规则等。随着对系统的理解加深，可以不断优化监控方案，提高系统的可观测性。