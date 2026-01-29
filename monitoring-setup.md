# 监控系统部署指南

本文档介绍如何部署和使用Prometheus + Grafana监控系统来监控JVM、Redis、MongoDB等关键指标。

## 部署步骤

### 1. 启动监控服务

```bash
# 启动完整的监控系统
docker-compose -f docker-compose-full.yml up -d

# 或者只启动监控组件
docker-compose -f docker-compose-monitoring.yml up -d
```

### 2. 验证监控服务

#### 验证Prometheus
访问 http://localhost:9090 来验证Prometheus正在运行

#### 验证Grafana
访问 http://localhost:3000 并使用以下凭据登录：
- 用户名: admin
- 密码: admin123

### 3. 配置Grafana数据源

1. 登录Grafana
2. 点击左侧菜单 "Connections" > "Data sources"
3. 点击 "Add new data source"
4. 选择 "Prometheus"
5. 设置URL为 `http://prometheus:9090`（如果是本地运行则为 `http://localhost:9090`）
6. 点击 "Save & test"

### 4. 导入预设仪表板

使用以下ID导入预设仪表板：

#### JVM监控仪表板
- ID: `4701` (Spring Boot 2.1 + JVM (Micrometer))

或者使用提供的仪表板JSON文件：
- `dashboard-configs/jvm-dashboard.json`

#### Redis监控仪表板
- ID: `763` (Redis Dashboard)

或者使用提供的仪表板JSON文件：
- `dashboard-configs/redis-dashboard.json`

#### MongoDB监控仪表板
- ID: `7353` (MongoDB Overview)

或者使用提供的仪表板JSON文件：
- `dashboard-configs/mongodb-dashboard.json`

## 监控指标详情

### JVM指标
- 内存使用情况 (heap/non-heap)
- CPU使用率
- 垃圾回收统计
- 线程数量

### Redis指标
- 内存使用情况
- 连接客户端数量
- 命令处理速率
- 键数量统计

### MongoDB指标
- 连接数统计
- 操作计数 (读/写/更新等)
- 内存使用情况
- 网络流量统计

## 自定义仪表板

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

## 故障排除

### 如果Grafana没有数据显示
1. 检查Prometheus是否能抓取到目标
2. 验证数据源URL是否正确
3. 检查时间范围设置

### 如果应用指标未显示
1. 确认应用正在运行
2. 检查应用的`/actuator/prometheus`端点是否可访问
3. 验证Prometheus配置中的目标地址

### 如果Redis/MongoDB指标未显示
1. 确认对应的exporter服务正在运行
2. 检查exporter是否能连接到数据库
3. 验证Prometheus配置中exporter的地址

## 安全建议

1. 更改Grafana默认密码
2. 在生产环境中配置Prometheus认证
3. 使用HTTPS加密传输
4. 限制对监控端点的访问权限