# NL2SQL - 自然语言转SQL查询服务

## 1. 功能概述

本服务实现了自然语言转SQL查询功能，允许用户通过自然语言描述查询需求，系统自动转换为SQL语句并执行查询，返回查询结果。

### 核心能力
- **自然语言理解**: 将用户输入的自然语言转换为标准SQL查询语句
- **SQL安全验证**: 严格验证SQL语句，防止恶意操作
- **数据库元信息获取**: 自动获取数据库表结构、字段信息
- **查询结果格式化**: 以友好的方式展示查询结果
- **错误处理优化**: 提供详细的错误提示和建议

### 支持的查询类型
- 数据统计查询（如：查询数量、求和、平均值等）
- 数据明细查询（如：查询特定条件的记录）
- 表结构查询（如：查询数据库中有多少张表）

### 安全限制
- **只读操作**: 仅支持SELECT查询，禁止INSERT、UPDATE、DELETE等修改操作
- **危险操作拦截**: 拦截DROP、ALTER、TRUNCATE等危险DDL操作
- **类型安全验证**: 验证SQL中的类型匹配，防止数值字段使用字符串比较
- **非法值检测**: 检测并拦截不合理的字符串字面值（如'default'、'null'等）

## 2. 架构设计

### 整体架构

```
用户输入 (自然语言)
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│                     意图识别层                                │
│  ChatTypeAssistant / ChatTypeAssistantStream                 │
│  识别用户意图，判断是否为 SQL_QUERY 类型                       │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│                     业务处理层                                │
│  ┌──────────────────┐  ┌──────────────────────────────────┐ │
│  │ ChatServiceImpl  │  │ StreamingDispatchService         │ │
│  │ (HTTP模式)       │  │ (SSE模式)                        │ │
│  └──────────────────┘  └──────────────────────────────────┘ │
│           │                       │                          │
│           ▼                       ▼                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              NL2SQLService                              │ │
│  │  核心服务：自然语言转SQL并执行查询                         │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│                     数据访问层                                │
│  Spring JdbcTemplate                                        │
│  执行SQL查询并返回结果                                        │
└─────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│                     MySQL数据库                               │
│  执行实际的SQL查询操作                                        │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 路径 | 说明 |
|------|------|------|
| NL2SQLService | `src/main/java/org/fb/service/impl/NL2SQLService.java` | 核心服务类，负责SQL生成和执行 |
| NaturalLanguageSQLAgent | `src/main/java/org/fb/service/assistant/NaturalLanguageSQLAgent.java` | AI接口，使用LLM生成SQL |
| ChatServiceImpl | `src/main/java/org/fb/service/impl/ChatServiceImpl.java` | HTTP模式业务处理器 |
| StreamingDispatchService | `src/main/java/org/fb/service/StreamingDispatchService.java` | SSE模式业务处理器 |
| LLMConfig | `src/main/java/org/fb/config/LLMConfig.java` | LLM模型配置类 |

## 3. 核心实现

### 3.1 NL2SQLService

```java
@Service
public class NL2SQLService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private NaturalLanguageSQLAgent languageSQLService;
    
    /**
     * 执行自然语言查询
     * 流程：自然语言 → 生成SQL → 验证SQL → 执行查询 → 返回结果
     */
    public List<Map<String, Object>> executeNaturalLanguageQuery(String naturalLanguage) {
        // 1. 生成SQL语句
        String sql = naturalLanguageToSQL(naturalLanguage);
        
        // 2. 验证SQL合法性
        validateSQL(sql);
        
        // 3. 执行SQL查询
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        
        return result;
    }
}
```

### 3.2 SQL生成流程

```java
public String naturalLanguageToSQL(String naturalLanguage) {
    // 1. 获取数据库Schema信息
    String schemaInfo = getDatabaseSchema();
    
    // 2. 构建提示词
    String prompt = buildPrompt(schemaInfo, naturalLanguage);
    
    // 3. 调用LLM生成SQL
    String sql = languageSQLService.convertToSQL(prompt);
    
    return sql;
}
```

### 3.3 数据库Schema获取

```java
private String getDatabaseSchema() {
    StringBuilder schema = new StringBuilder();
    
    // 获取所有表信息
    List<Map<String, Object>> tables = jdbcTemplate.queryForList("""
        SELECT TABLE_NAME, TABLE_COMMENT 
        FROM INFORMATION_SCHEMA.TABLES 
        WHERE TABLE_SCHEMA = DATABASE()
        """);
    
    for (Map<String, Object> table : tables) {
        String tableName = (String) table.get("TABLE_NAME");
        
        schema.append(String.format("表: %s (%s)\n", tableName, table.get("TABLE_COMMENT")));
        
        // 获取表字段信息
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
            SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """, tableName);
        
        for (Map<String, Object> column : columns) {
            schema.append(String.format("  - %s: %s %s (%s)\n",
                column.get("COLUMN_NAME"),
                column.get("DATA_TYPE"),
                "YES".equals(column.get("IS_NULLABLE")) ? "NULL" : "NOT NULL",
                column.get("COLUMN_COMMENT")
            ));
        }
        schema.append("\n");
    }
    
    return schema.toString();
}
```

### 3.4 SQL安全验证

```java
private void validateSQL(String sql) {
    // 1. 检查空SQL
    if (sql == null || sql.trim().isEmpty()) {
        throw new RuntimeException("生成的SQL语句为空");
    }
    
    // 2. 检查危险操作
    String upperSql = sql.toUpperCase();
    if (upperSql.contains("DROP") || upperSql.contains("DELETE") || 
        upperSql.contains("UPDATE") || upperSql.contains("INSERT") ||
        upperSql.contains("ALTER") || upperSql.contains("TRUNCATE")) {
        throw new RuntimeException("不允许执行修改数据的SQL操作");
    }
    
    // 3. 检查非法字符串字面值
    if (sql.contains("'default'") || sql.contains("'DEFAULT'") || 
        sql.contains("'null'") || sql.contains("'NULL'")) {
        throw new RuntimeException("生成的SQL包含不合理的字符串字面量");
    }
    
    // 4. 验证类型匹配
    validateSQLTypes(sql);
}
```

### 3.5 类型验证

```java
private void validateSQLTypes(String sql) {
    // 获取所有数值类型字段
    List<Map<String, Object>> numericColumns = jdbcTemplate.queryForList("""
        SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND DATA_TYPE IN ('int', 'bigint', 'tinyint', 'smallint', 'decimal', 'float', 'double')
        """);
    
    // 检查数值字段是否使用了字符串比较
    for (Map<String, Object> column : numericColumns) {
        String tableName = (String) column.get("TABLE_NAME");
        String columnName = (String) column.get("COLUMN_NAME");
        
        // 构建匹配模式：表名.字段名 = 'xxx' 或 字段名 = 'xxx'
        String pattern1 = String.format("%s\\.%s\\s*=\\s*'[^']*'", tableName, columnName);
        String pattern2 = String.format("%s\\s*=\\s*'[^']*'", columnName);
        
        if (sql.matches(".*" + pattern1 + ".*") || sql.matches(".*" + pattern2 + ".*")) {
            throw new RuntimeException(String.format(
                "SQL中对数值字段 %s (类型: %s) 使用了字符串比较", 
                columnName, column.get("DATA_TYPE")));
        }
    }
}
```

## 4. 提示词设计

### 4.1 系统角色定义

```
你是一个MySQL数据库专家，负责将自然语言转换为SQL查询语句并执行查询返回结果。
```

### 4.2 核心规则

1. **操作类型限制**
   - 只生成SELECT查询语句
   - 禁止生成INSERT、UPDATE、DELETE等修改数据的语句
   - 禁止生成DROP、ALTER、TRUNCATE等DDL操作

2. **语法规范**
   - 只使用数据库中存在的表名和字段名
   - 字符串字段使用单引号包裹
   - 数值字段不使用引号
   - 日期字段使用标准格式（如'2024-01-01'）

3. **安全限制**
   - 绝对不使用'default'、'null'等作为字符串字面值
   - 数值类型字段不使用字符串比较

4. **查询模板**
   - 查询表数量：`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()`
   - 查询数据行数：`SELECT COUNT(*) FROM 表名`

### 4.3 完整提示词示例

```text
你是一个MySQL专家。基于以下数据库结构，将自然语言转换为SQL查询语句。
只返回SQL语句，不要任何解释。

重要注意事项：
1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE等修改数据的语句
2. 确保SQL语句中的所有字段和表名都存在于提供的数据库结构中
3. 对于统计查询（如查询表数量、数据数量等），使用正确的聚合函数和统计方法
4. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值
5. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容，不能是关键字
6. 对于数值字段，不要使用引号，直接使用数字
7. 对于日期字段，使用标准的日期格式，如'2024-01-01'
8. 如果不确定如何转换，返回SELECT 1语句
9. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配
10. 如果字段类型是BIGINT、INT等数值类型，不要使用字符串比较
11. 查询表数量时，使用: SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
12. 查询数据行数时，使用: SELECT COUNT(*) FROM 表名

数据库结构：
表: appointment (挂号信息表)
  - id: bigint NOT NULL (主键ID)
  - patient_name: varchar NOT NULL (患者姓名)
  - doctor_id: int NOT NULL (医生ID)
  ...

用户查询：查询数据库中有多少张表

SQL语句：
```

## 5. 业务集成

### 5.1 HTTP模式集成

```java
// ChatServiceImpl.java
@Autowired
private NL2SQLService nl2SQLService;

private String processByUserMeanings(Long memoryId, String userMessage) {
    // ... 意图识别 ...
    
    if (BusinessConstant.SQL_OPERATION_TYPE.equals(intent)) {
        try {
            List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(userMessage);
            if (sqlResult == null || sqlResult.isEmpty()) {
                result = "查询结果为空";
            } else {
                result = formatQueryResult(sqlResult);
            }
        } catch (Exception e) {
            // 友好的错误处理
            result = handleSQLException(e);
        }
    }
    
    return result;
}

private String formatQueryResult(List<Map<String, Object>> result) {
    StringBuilder sb = new StringBuilder();
    sb.append("查询成功，共找到").append(result.size()).append("条记录：\n\n");
    
    int count = 1;
    for (Map<String, Object> row : result) {
        sb.append("记录").append(count++).append(":\n");
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
    }
    
    return sb.toString();
}
```

### 5.2 SSE模式集成

```java
// StreamingDispatchService.java
@Autowired
private NL2SQLService nl2SQLService;

private Flux<String> processWithNaturalLanguageSQLAgent(String userMessage) {
    try {
        List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(userMessage);
        String result;
        if (sqlResult == null || sqlResult.isEmpty()) {
            result = "查询结果为空";
        } else {
            result = formatQueryResult(sqlResult);
        }
        return Flux.just(result);
    } catch (Exception e) {
        return Flux.just(handleSQLException(e));
    }
}
```

## 6. 错误处理

### 6.1 错误类型

| 错误类型 | 错误信息 | 处理建议 |
|---------|---------|---------|
| 空SQL | 生成的SQL语句为空 | 检查输入是否明确 |
| 危险操作 | 不允许执行修改数据的SQL操作 | 仅支持SELECT查询 |
| 非法值 | 生成的SQL包含不合理的字符串字面量 | 使用具体的查询值 |
| 类型不匹配 | 数值字段使用了字符串比较 | 使用数值而非字符串 |
| 执行失败 | 查询失败: xxx | 检查数据库连接 |

### 6.2 友好错误提示

```java
private String handleSQLException(Exception e) {
    String errorMsg = e.getMessage();
    
    if (errorMsg != null && errorMsg.contains("Failed to convert from type")) {
        return "抱歉，SQL查询时出现类型转换错误。\n\n" +
               "建议：\n" +
               "1. 请尝试更具体地描述您的查询需求\n" +
               "2. 如果查询涉及数值字段，请明确说明数值范围\n" +
               "错误详情：" + errorMsg;
    } else if (errorMsg != null && errorMsg.contains("不合理的字符串字面量")) {
        return "抱歉，AI生成的SQL包含不合理的值。\n\n" +
               "建议：\n" +
               "1. 避免使用\"default\"、\"null\"等关键字作为查询值\n" +
               "2. 使用具体的数值或文本进行查询";
    } else {
        return "抱歉，执行SQL查询时出错：" + errorMsg + "。\n\n" +
               "建议：请尝试用更清晰、更具体的方式描述您的查询需求。";
    }
}
```

## 7. 配置说明

### 7.1 数据库配置

```yaml
# application-standalone.yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/mydocker?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
    username: root
    password: root
```

### 7.2 LLM模型配置

```yaml
# application-standalone.yml
ai:
  deepSeek:
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat
    apiKey: ${DeepSeek_API_KEY:}
```

### 7.3 环境变量

```bash
# 设置DeepSeek API Key
export DeepSeek_API_KEY="your_api_key"

# 或在Windows中
set DeepSeek_API_KEY=your_api_key
```

## 8. 使用示例

### 8.1 查询数据库表数量

**用户输入**: `当前连接的mysql数据库中有多少张表？`

**生成SQL**: 
```sql
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
```

**返回结果**:
```
查询成功，共找到1条记录：

记录1:
  COUNT(*): 3
```

### 8.2 查询fileoperation表数据量

**用户输入**: `查询fileoperation表中有多少条数据？`

**生成SQL**:
```sql
SELECT COUNT(*) FROM fileoperation
```

**返回结果**:
```
查询成功，共找到1条记录：

记录1:
  COUNT(*): 150
```

### 8.3 查询特定记录

**用户输入**: `查询id为1的患者挂号信息`

**生成SQL**:
```sql
SELECT * FROM appointment WHERE id = 1
```

## 9. 部署说明

### 9.1 IDEA本地启动

1. 确保MySQL服务已启动（默认localhost:3306）
2. 配置`application-standalone.yml`中的数据库连接
3. 配置DeepSeek API Key环境变量
4. 运行主类 `RAGTranslationApplication`

### 9.2 Docker部署

使用`deploy-desktop.bat`脚本部署：

```bash
deploy-desktop.bat
```

脚本会自动：
1. 检查并启动必要的中间件（MySQL、MongoDB、Redis、Kafka、Nacos）
2. 构建Java应用
3. 构建Docker镜像
4. 启动Docker容器

### 9.3 访问地址

| 模式 | 地址 | 说明 |
|------|------|------|
| HTTP模式 | http://localhost:8020/unified.html | 统一客户端页面 |
| SSE模式 | http://localhost:8020/chat-sse.html | SSE实时推送页面 |

## 10. 技术细节

### 10.1 依赖组件

```xml
<!-- Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<!-- LangChain4j -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

### 10.2 性能考虑

- 数据库Schema信息在每次查询时获取，可考虑缓存
- LLM调用可能较慢，可添加超时控制
- 大结果集查询可能影响性能，建议限制返回条数

### 10.3 安全增强建议

1. **SQL注入防护**: 虽已禁止危险操作，但仍建议使用参数化查询
2. **查询限流**: 可添加查询频率限制，防止滥用
3. **权限控制**: 可根据用户权限控制可查询的表范围
4. **审计日志**: 记录所有SQL查询操作，便于问题排查

## 11. 常见问题

### Q1: 为什么返回的是SQL语句而不是查询结果？

**原因**: SSE模式使用了错误的模型配置  
**解决**: 修改`LLMConfig.java`，将`streamingChatModel`切换到DeepSeek

### Q2: 查询结果显示1条记录，但实际有多条？

**原因**: AI生成的SQL不正确，可能返回了常量值  
**解决**: 检查NL2SQLService的提示词是否包含正确的查询模板

### Q3: 出现类型转换错误？

**原因**: AI生成的SQL中，数值字段使用了字符串比较  
**解决**: 在提示词中强调数值类型字段不要使用引号

### Q4: 如何切换到其他LLM模型？

**解决**: 修改`application-standalone.yml`中的模型配置，或修改`LLMConfig.java`中的模型初始化逻辑

## 12. 更新日志

| 版本 | 日期 | 修改内容 |
|------|------|---------|
| v1.0 | 2026-01-19 | 初始版本，支持HTTP和SSE模式 |
