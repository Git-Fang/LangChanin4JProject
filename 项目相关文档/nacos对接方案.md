# Nacos配置管理与服务注册适配方案

## 一、方案概述

本项目通过集成Alibaba Nacos实现配置中心和服务注册发现功能，支持配置的集中管理和动态更新，同时实现服务注册功能。

### 1.1 目标
- 实现application-docker.yml配置通过Nacos进行管理
- 支持配置动态更新，无需重启应用
- 实现服务注册到Nacos
- 兼容deploy-desktop.bat和IDEA本地启动两种方式

### 1.2 技术选型
- Nacos版本: v2.4.2
- Nacos Config: nacos-config-spring-boot3-starter 0.2.13
- Nacos Discovery: nacos-discovery-spring-boot3-starter 0.2.13

## 二、当前现状分析

### 2.1 已有的Nacos配置
- **pom.xml**: 已包含nacos-config-spring-boot3-starter:0.2.13
- **application-docker.yml**: 已有基础Nacos配置中心配置
- **deploy-desktop.bat**: 已在步骤5启动Nacos v2.4.2

### 2.2 现有配置示例(application-docker.yml)
```yaml
nacos:
  config:
    server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
    namespace: ${NACOS_NAMESPACE:}
    group: DEFAULT_GROUP
    data-id: application-docker.yml
    type: yaml
    auto-refresh: true
    bootstrap:
      enable: true
```

### 2.3 现有Nacos启动配置(deploy-desktop.bat)
```bat
docker run -d --name nacos --network ai-network -p 8848:8848 -e MODE=standalone -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai nacos/nacos-server:v2.4.2
```

## 三、适配改造方案

### 3.1 依赖配置(pom.xml)

添加Spring Cloud Alibaba Nacos依赖:

```xml
<!-- Spring Cloud 版本管理 (在dependencyManagement中添加) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>2023.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Nacos Config 配置中心 (Spring Boot 3.x兼容) -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    <version>2023.0.3.4</version>
</dependency>

<!-- Nacos Discovery 服务注册与发现 (Spring Boot 3.x兼容) -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    <version>2023.0.3.4</version>
</dependency>
```

### 3.2 启用服务注册(RAGTranslationApplication.java)

```java
@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@MapperScan("mapper")
@EnableDiscoveryClient  // 新增: 启用服务注册发现
public class RAGTranslationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RAGTranslationApplication.class, args);
    }
}
```

### 3.3 配置文件修改

#### 3.3.1 application-docker.yml
添加服务注册配置到`spring.cloud.nacos`下:

```yaml
spring:
  # ... 现有配置保持不变 ...

  # Nacos 配置中心和服务注册
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: DEFAULT_GROUP
        data-id: application-docker.yml
        type: yaml
        auto-refresh: true

      # 服务注册配置
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: DEFAULT_GROUP
        service: ${spring.application.name}
        cluster-name: docker
```

#### 3.3.2 application-standalone.yml
同步添加服务注册配置:

```yaml
spring:
  # ... 现有配置保持不变 ...

  # Nacos 配置中心和服务注册
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: DEFAULT_GROUP
        data-id: application-standalone.yml
        type: yaml
        auto-refresh: true

      # 服务注册配置
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: DEFAULT_GROUP
        service: ${spring.application.name}
```

### 3.4 配置动态刷新支持(EnvConf.java)

为配置类添加@RefreshScope注解:

```java
@Configuration
@RefreshScope  // 新增: 启用配置动态刷新
public class EnvConf {
    // ... 现有代码保持不变 ...
}
```

### 3.5 部署脚本修改(deploy-desktop.bat)

在docker run命令中添加Nacos环境变量:

```bat
docker run -d --name %CONTAINER_NAME% --network ai-network -p %APP_PORT%:%APP_PORT% ^
    --env-file .env ^
    -e SPRING_PROFILES_ACTIVE=docker ^
    -e NACOS_SERVER_ADDR=nacos:8848 ^  // 新增
    -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true ^
    -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chat_db ^
    -e SPRING_REDIS_HOST=redis ^
    -e SPRING_REDIS_PORT=6379 ^
    -e AI_EMBEDDINGSTORE_QDRANT_HOST=host.docker.internal ^
    -e AI_EMBEDDINGSTORE_QDRANT_PORT=6334 ^
    -e spring.kafka.bootstrap-servers=kafka:9092 ^
    -e TZ=Asia/Shanghai ^
    %IMAGE_NAME%:latest
```

### 3.6 环境变量模板(.env.example)

添加Nacos配置说明:

```properties
# AI服务API密钥配置示例
# 将此文件复制为 .env 并填入实际密钥

# Nacos配置中心地址
NACOS_SERVER_ADDR=localhost:8848

# Nacos命名空间(可选)
# NACOS_NAMESPACE=

# DeepSeek API密钥
DeepSeek_API_KEY=your_deepseek_api_key_here

# Kimi（Moonshot）API密钥
KIMI_API_KEY=your_kimi_api_key_here

# 阿里云通义千问API密钥
DASHSCOPE_API_KEY=your_dashscope_api_key_here

# 百度地图API密钥
BAIDU_MAP_API_KEY=your_baidu_map_api_key_here
```

## 四、修改文件汇总

| 序号 | 文件 | 修改类型 | 说明 |
|------|------|---------|------|
| 1 | pom.xml | 修改 | 添加nacos-discovery依赖和Spring Cloud版本管理 |
| 2 | RAGTranslationApplication.java | 修改 | 添加@EnableDiscoveryClient注解 |
| 3 | application-docker.yml | 修改 | 添加spring.cloud.nacos配置 |
| 4 | application-standalone.yml | 修改 | 添加spring.cloud.nacos配置 |
| 5 | EnvConf.java | 修改 | 添加@RefreshScope注解 |
| 6 | TraceIdFilter.java | 修改 | 添加SSE路径排除，支持所有HTML页面 |
| 7 | LLMConfig.java | 修改 | 添加@RefreshScope支持动态刷新 |
| 8 | AiConf.java | 修改 | 移除EnvConf依赖，直接使用@Value |
| 9 | RedisConfig.java | 修改 | 统一Redis配置，支持Docker环境 |
| 10 | WebMvcConfig.java | 修改 | 添加消息转换器配置 |
| 11 | GlobalExceptionHandler.java | 修改 | 排除SSE请求的错误处理 |
| 12 | ConfigRefreshController.java | 新增 | 配置刷新控制器 |
| 13 | deploy-desktop.bat | 修改 | 添加Nacos环境变量 |
| 14 | .env.example | 修改 | 添加Nacos配置说明 |

## 五、Nacos控制台配置

### 5.1 访问Nacos控制台
- URL: http://localhost:8848/nacos
- 默认用户名: nacos
- 默认密码: nacos

### 5.2 创建配置

#### 步骤1: 创建配置
1. 登录Nacos控制台
2. 进入"配置管理" -> "配置列表"
3. 点击"+"号创建新配置
4. 填写配置信息:

```
Data ID: application-docker.yml
Group: DEFAULT_GROUP
配置格式: YAML
配置内容: (复制application-docker.yml中的所有配置)
```

#### 步骤2: 验证配置加载
1. 启动应用
2. 查看应用日志，确认从Nacos加载配置
3. 检查配置是否生效

### 5.3 配置动态更新

1. 在Nacos控制台修改配置
2. 应用会自动感知配置变化
3. 使用@RefreshScope注解的bean会自动刷新
4. 无需重启应用

## 六、启动方式说明

### 6.1 deploy-desktop.bat启动
```bash
# 执行部署脚本
deploy-desktop.bat

# 脚本会自动:
# 1. 启动Nacos服务
# 2. 构建应用镜像
# 3. 启动应用容器并注册到Nacos
```

### 6.2 IDEA本地启动
```bash
# 在Run Configuration中添加环境变量:
SPRING_PROFILES_ACTIVE=standalone
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=

# 或者在.env文件中配置后运行
```

### 6.3 Docker单独启动
```bash
# 如果Nacos在其他机器上
docker run -d --name ragtranslation-app \
    -p 8000:8000 \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e NACOS_SERVER_ADDR=your-nacos-server:8848 \
    -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true \
    ragtranslation-app:latest
```

## 七、验证步骤

### 7.1 Nacos服务验证
1. 检查Nacos容器是否运行: `docker ps | grep nacos`
2. 访问Nacos控制台: http://localhost:8848/nacos
3. 查看"服务管理" -> "服务列表"，确认RAGTranslationApplication已注册

### 7.2 配置中心验证
1. 在Nacos中创建测试配置
2. 重启应用或等待配置刷新
3. 验证应用是否正确加载Nacos配置

### 7.3 动态更新验证
1. 修改Nacos中的配置(如日志级别)
2. 观察应用日志变化
3. 确认配置已动态生效

### 7.4 功能验证
1. 访问应用首页: http://localhost:8000/
2. 测试聊天功能
3. 验证所有API接口正常

## 八、注意事项

### 8.1 向后兼容
- 所有修改都保留了环境变量覆盖能力
- 不影响现有部署方式
- 本地开发和Docker部署都能正常工作

### 8.2 动态刷新
- 只有添加@RefreshScope注解的配置才会动态更新
- 需要动态刷新的配置类需要添加该注解

### 8.3 服务注册
- 应用启动后会在Nacos中注册为"RAGTranslationApplication"
- 服务名称由`spring.application.name`决定

### 8.4 网络配置
- Docker部署时使用容器名"nacos"作为Nacos地址
- 本地开发使用localhost:8848

### 8.5 命名空间
- 默认使用空命名空间
- 如需使用命名空间，设置NACOS_NAMESPACE环境变量

## 九、常见问题

### Q1: 应用无法连接Nacos?
检查以下几点:
1. Nacos服务是否启动: `docker ps | grep nacos`
2. Nacos地址配置是否正确
3. 网络是否连通(特别是Docker容器内)
4. 防火墙是否阻止连接

### Q2: 配置不生效?
1. 确认Data ID和Group配置正确
2. 检查配置格式是否为YAML
3. 查看应用日志中的配置加载信息
4. 确认bootstrap.enable=true

### Q3: 如何查看应用从Nacos加载的配置?
1. 在Nacos控制台查看配置内容
2. 查看应用启动日志中的配置信息
3. 使用 actuator/refresh 端点刷新配置

### Q4: 本地开发和Docker部署如何切换?
通过SPRING_PROFILES_ACTIVE环境变量:
- 本地开发: standalone
- Docker部署: docker

## 十、版本信息

- Nacos版本: v2.4.2
- Spring Boot版本: 3.5.0
- Spring Cloud版本: 2023.0.3
- Spring Cloud Alibaba版本: 2023.0.3.4
- Java版本: 17

## 十一、问题修复记录

### 11.1 chat-sse.html页面无法显示问题

**问题描述**: 添加Nacos依赖后，/chat-sse.html页面无法显示和请求。

**问题原因**: TraceIdFilter过滤器未排除SSE流式请求路径，导致SSE连接被阻塞。

**修复方案**: 修改`TraceIdFilter.java`，添加路径排除：

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/mcp/sse") 
        || path.startsWith("/mcp/messages") 
        || path.startsWith("/xiaozhi/chat/stream");
}
```

**修改文件**: `src/main/java/org/fb/config/TraceIdFilter.java`

**排除的路径**:
- `/mcp/sse` - SSE连接端点 (unified.html, mcp-test.html使用)
- `/mcp/messages` - SSE消息发送端点 (unified.html, mcp-test.html使用)
- `/xiaozhi/chat/stream/{requestId}` - SSE流式结果端点 (chat-sse.html使用)

### 11.3 spring.config.import配置缺失问题

**问题描述**: Docker启动时报错"No spring.config.import property has been defined"

**问题原因**: Spring Cloud Alibaba Nacos 2023.x版本需要显式声明config.import属性来启用Nacos配置导入。

**修复方案**: 在application-docker.yml和application-standalone.yml中添加config.import配置:

```yaml
spring:
  application:
    name: RAGTranslationApplication
  config:
    import:
      - optional:nacos:application-docker.yml  # 对应standalone则为application-standalone.yml
```

**修改文件**: 
- `src/main/resources/application-docker.yml`
- `src/main/resources/application-standalone.yml`

**说明**: 使用`optional:`前缀表示Nacos配置是可选的，如果Nacos服务不可用，应用仍可正常启动使用本地配置。

### 11.6 Spring Boot版本兼容性问题

**问题描述**: Docker启动时报错"Spring Boot [3.5.0] is not compatible with this Spring Cloud release train"

**问题原因**: Spring Cloud 2023.0.3官方支持的Spring Boot版本为3.2.x或3.3.x，而项目使用的是3.5.0。

**修复方案**: 禁用Spring Cloud版本兼容验证器：

```yaml
spring:
  cloud:
    compatibility-verifier:
      enabled: false
```

**修改文件**: 
- `src/main/resources/application-docker.yml`
- `src/main/resources/application-standalone.yml`

**说明**: 此设置允许在较新版本的Spring Boot上运行Spring Cloud组件，通常不会影响功能正常使用。

### 11.7 动态配置刷新功能

**功能描述**: 实现Nacos配置动态刷新，支持在不重启服务的情况下更新配置。

**实现方案**:

1. **配置类添加@RefreshScope**:

```java
@Configuration
@RefreshScope
public class LLMConfig {
    @Value("${ai.deepSeek.model:deepseek-chat}")
    private volatile String deepSeekModel;
    
    // ... 其他配置属性
}
```

2. **配置刷新控制器**:

```java
@RestController
@RequestMapping("/config")
public class ConfigRefreshController {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @PostMapping("/refresh")
    public Map<String, Object> refreshConfig() {
        eventPublisher.publishEvent(new RefreshScopeRefreshedEvent());
        return Map.of("success", true, "message", "配置已刷新");
    }
}
```

**使用步骤**:

1. **在Nacos控制台修改配置**:
   - 访问 http://localhost:8848/nacos
   - 修改配置后点击发布

2. **调用刷新接口**:
   ```bash
   curl -X POST http://localhost:8000/config/refresh
   ```

3. **验证配置已更新**:
   - 新的API调用将使用更新后的配置

**支持的动态刷新配置**:
- ✅ API密钥（DeepSeek、Kimi、DashScope等）
- ✅ 模型名称（ai.deepSeek.model等）
- ✅ base-url配置
- ✅ 功能开关配置

**无法动态刷新的配置**（需要重启服务）:
- ❌ 数据库连接池配置
- ❌ Redis连接配置
- ❌ Qdrant连接配置

**修改文件**:
- `src/main/java/org/fb/config/LLMConfig.java` - 添加@RefreshScope
- `src/main/java/org/fb/controller/ConfigRefreshController.java` - 新建刷新控制器

### 11.8 依赖版本兼容性问题

**问题描述**: 原始使用的`com.alibaba.boot:nacos-*-spring-boot3-starter:0.2.13`在Spring Boot 3.x环境下无法找到依赖。

**解决方案**: 改用Spring Cloud Alibaba版本:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    <version>2023.0.3.4</version>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    <version>2023.0.3.4</version>
</dependency>
```

同时添加Spring Cloud版本管理:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>2023.0.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

## 十二、附录

### 12.1 相关文件路径
```
pom.xml                                           # Maven配置
src/main/resources/application-docker.yml         # Docker环境配置
src/main/resources/application-standalone.yml     # 本地环境配置
src/main/java/org/fb/RAGTranslationApplication.java  # 主应用类
src/main/java/org/fb/config/EnvConf.java          # 环境配置类
src/main/java/org/fb/config/TraceIdFilter.java    # 链路追踪过滤器
src/main/java/org/fb/config/LLMConfig.java        # LLM配置类
src/main/java/org/fb/controller/ConfigRefreshController.java  # 配置刷新控制器
deploy-desktop.bat                                # Docker部署脚本
.env.example                                      # 环境变量模板
```

### 12.2 Nacos官方文档
- Nacos官方文档: https://nacos.io/docs/what-is-nacos/
- Nacos Spring Boot集成: https://github.com/nacos-group/nacos-spring-boot-project
- Spring Cloud Alibaba: https://github.com/alibaba/spring-cloud-alibaba

### 12.3 监控和管理
- Nacos控制台: http://localhost:8848/nacos
- 应用健康检查: http://localhost:8000/actuator/health
- 配置刷新接口: POST http://localhost:8000/config/refresh
