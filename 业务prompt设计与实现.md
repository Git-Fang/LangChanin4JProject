# 业务Prompt设计与实现文档

## 文档概述

本文档全面整理了当前项目中各业务场景的Prompt设计及其相关业务逻辑实现。项目采用Spring Boot + LangChain4j架构，通过意图识别机制将用户请求路由到不同的专业助手（Agent），每个助手对应不同的Prompt模板和业务工具。

---

## 一、业务架构总览

### 1.1 核心业务流程

```
用户输入 → 意图识别(ChatTypeAssistant) → 路由分发 → 专业Agent处理 → 返回结果
            ↑
    根据intent类型选择对应的assistant
```

### 1.2 意图类型与Agent对应关系

| 意图类型 | 常量值 | 对应Assistant | Prompt文件 | 业务场景 |
|---------|--------|---------------|------------|---------|
| 医疗健康 | `medical` | DoctorAgent | doctorAgent-prompt-template.txt | 医院挂号、预约、医生查询 |
| 翻译服务 | `translation` | TranslaterService | translate-prompt.txt | 多语言翻译 |
| 术语提取 | `term_extraction` | TermExtractionAgent | termExtractionAgent-prompt-template.txt | 专业术语提取 |
| SQL操作 | `sql_transfer` | NL2SQLService | database-operation-prompt.txt | 数据库查询 |
| 默认类型 | `general` | ChatAssistant | default-prompt.txt | 通用知识问答、个人助手 |

### 1.3 常量定义（BusinessConstant.java）

```java
public class BusinessConstant {
    // 意图类型常量
    public static final String MEDICAL_TYPE = "medical";
    public static final String TRANSLATION_TYPE = "translation";
    public static final String TERM_EXTRACTION_TYPE = "term_extraction";
    public static final String SQL_OPERATION_TYPE = "sql_transfer";
    public static final String DEFAULT_TYPE = "general";
    
    // 业务配置常量
    public static final int MAX_PARAGRAPH_LENGTH = 1500;
    public static final int THREAD_POOL_SIZE = 15;
    public static final int THREAD_POOL_QUEUE_SIZE = 100;
}
```

---

## 二、意图识别与路由机制

### 2.1 意图识别Prompt（chat-type-prompt.txt）

**文件位置**: `src/main/resources/chat-type-prompt.txt`

**核心职责**: 根据用户输入内容判断意图类型，返回JSON格式的分类结果。

**Prompt结构**:
```
【一、专业能力】
- 本地知识库助手：默认能力
- 术语提取助手：专业术语提取
- 翻译助手：多语种翻译
- 医疗助手：医疗领域咨询

【二、意图分类】
- sql_transfer: 明确要求查询数据库数据
- medical: 医疗、医院相关内容
- translation: 多语言翻译操作
- term_extraction: 术语相关操作
- general: 其他一般性对话
```

**输出格式**:
```json
{"intent":"medical","confidence":"0.95"}
```

### 2.2 意图识别服务实现

**核心类**: `ChatServiceImpl.processByUserMeanings()`

**处理流程**:
```
1. 调用ChatTypeAssistant进行意图识别
2. 解析AI返回的JSON，提取intent字段
3. 根据intent类型选择对应的业务处理服务
4. 调用对应的Agent处理用户请求
5. 保存聊天记录到数据库
```

**路由分发逻辑**:
```java
if (MEDICAL_TYPE.equals(intent)) {
    result = doctorAgent.chat(memoryId, userMessage);
} else if (TRANSLATION_TYPE.equals(intent)) {
    result = translaterService.translate(memoryId, userMessage);
} else if (TERM_EXTRACTION_TYPE.equals(intent)) {
    result = termExtractionAgent.chat(userMessage);
} else if (SQL_OPERATION_TYPE.equals(intent)) {
    List<Map<String, Object>> sqlResult = nl2SQLService.executeNaturalLanguageQuery(userMessage);
    result = formatQueryResult(sqlResult);
} else {
    result = chatAssistant.chat(memoryId, userMessage);
}
```

### 2.3 意图识别优化（sql_transfer判断）

**问题背景**: AI可能将数据库设计、优化等讨论误识别为sql_transfer。

**优化方案**: 在`NL2SQLService`中添加预判断逻辑`shouldQueryDatabase()`。

**判断逻辑**:
```java
// 需要查询数据库的关键词模式
QUERY_DATABASE_PATTERN = 
    "(查询|获取|查找|统计|列出|显示|有多少|多少个|哪些|有几个|显示所有|看看|查看|检索|搜索)\\s*.*"

// 不需要查询数据库的模式（数据库概念讨论）
DATABASE_CONCEPT_PATTERN = 
    "(怎么设计|如何设计|如何优化|如何提高|优化|性能|索引|缓存|表结构|设计原则|原理|是什么)...数据库.*|" +
    "...数据库\\s*(怎么设计|如何设计|如何优化)...|" +
    "(SQL|sql)\\s*(怎么|如何|是什么|语法|语句|写法)...|" +
    "(mysql|MySQL|SqlServer|Oracle|PostgreSQL|数据库).*\\b(优化|调优|性能|原理|架构|设计)...\\b"
```

---

## 三、业务场景详细设计

### 3.1 医疗助手（DoctorAgent）

**文件位置**: 
- Prompt: `src/main/resources/doctorAgent-prompt-template.txt`
- Agent接口: `src/main/java/org/fb/service/assistant/DoctorAgent.java`
- 工具类: `src/main/java/org/fb/tools/AppointmentTools.java`

#### 3.1.1 Prompt设计

**角色定义**:
```
你的名字是"硅谷小智"，你是一家名为"北京协和医院"的智能客服。
你是一个训练有素的医疗顾问和医疗伴诊助手。
```

**最高优先级规则**:
```markdown
【最高优先级-工具调用总则】
当你需要执行任何涉及数据库操作的功能时（包括但不限于：预约挂号、取消挂号、查询预约记录、查询医生预约情况），你必须调用相应的系统工具来获取或操作数据！
- 绝对禁止在没有调用工具的情况下，直接告诉用户"预约成功"、"查询完成"等信息
- 绝对禁止自行生成、编造或猜测任何数据
- 工具调用是你获取或操作数据的唯一途径
```

#### 3.1.2 预约挂号流程

**Step 1: 获取完整预约信息**
- 患者姓名（必填）
- 身份证号（必填）
- 预约科室（必填）
- 预约日期（必填，格式：2026-02-04）
- 预约时间（必填，只能是"上午"或"下午"）
- 预约医生（必填）

**Step 2: 查询号源**
- 调用 `queryDepartment` 工具

**Step 3: 展示并让用户确认**

**Step 4: 【强制】调用bookAppointment工具**
```java
@Tool(name="book_appointment", value = "预约挂号：根据参数，先执行工具方法queryDepartment查询是否可预约...")
public String bookAppointment(Appointment appointment);
```

**Step 5: 展示预约结果**

#### 3.1.3 医生预约查询流程

**核心原则**: 
```
你没有实时数据库访问权限！你脑海中没有任何预约数据！
所有预约数据必须通过query_doctor_appointments工具获取！
```

**正确响应流程**:
```
Step 1: 从用户消息中提取医生姓名（如"顾浩然医生"提取"顾浩然"）
Step 2: 调用query_doctor_appointments工具
Step 3: 检查工具返回结果：
        - 如果返回空列表[]：告诉用户"该医生暂无预约记录"
        - 如果返回有数据：原样展示工具返回的预约信息
```

#### 3.1.4 可用工具清单

| 工具名称 | 功能描述 | 参数说明 |
|---------|---------|---------|
| `book_appointment` | 预约挂号 | Appointment对象（包含患者信息、科室、日期、时间、医生） |
| `cancel_appointment` | 取消预约 | Appointment对象 |
| `query_department` | 查询号源 | name, date, time, doctorName |
| `query_info` | 查询患者挂号信息 | username, idCard |
| `query_doctor_appointments` | 查询医生预约列表 | doctorName |
| `baidu_map_mcp` | 百度地图导航 | userMessage |

---

### 3.2 翻译助手（TranslaterService）

**文件位置**:
- Prompt: `src/main/resources/translate-prompt.txt`
- Agent接口: `src/main/java/org/fb/service/assistant/TranslaterService.java`
- 辅助Prompt: `src/main/resources/translation-term-extractor-prompt-template.txt`

#### 3.2.1 Prompt设计

**角色定义**: 专业的多国语言翻译专家

**核心任务**: 将用户输入的文本翻译成目标语言

#### 3.2.2 翻译工作流程

```
Step 1: 术语查询
    调用 CommonTools.find_similar_terms() 查询向量数据库中是否有相似术语

Step 2: 提取术语
    调用 TranslationTermExtractor.extractTerms() 从原文中提取术语词汇

Step 3: 查询术语翻译
    调用 CommonTools.embedding_search_for_terms() 查询术语翻译

Step 4: 执行翻译
    调用 CommonTools.do_translation() 生成翻译结果
```

#### 3.2.3 输出格式

**输入格式** (do_translation返回):
```
RESULT|原文:xxx|纠正后:xxx|命中术语:xxx|目标语言:xxx
```

**输出格式** (AI必须严格按照此格式输出):
```markdown
【待处理术语】
<命中的术语词汇，如果没有则显示"无">

【替换后文本】
<经过术语替换后的文本，如果没有替换则显示原文>

【最终翻译】
<完整的翻译结果 - 根据"目标语言"将"替换后文本"完整翻译>
```

#### 3.2.4 重要规则

1. 【强制】必须调用所有4个工具
2. 【强制】严格按照上述格式输出，不要添加任何其他内容
3. 【强制】"最终翻译"必须完整翻译"替换后文本"的所有内容
4. 【强制】不要输出工具调用过程信息
5. 【强制】不要输出解释性文字

---

### 3.3 术语提取助手（TermExtractionAgent）

**文件位置**:
- Prompt: `src/main/resources/termExtractionAgent-prompt-template.txt`
- Agent接口: `src/main/java/org/fb/service/assistant/TermExtractionAgent.java`

#### 3.3.1 Prompt设计

**角色定义**: 通晓多门学科、行业内容的专业术语词汇提取工程师

#### 3.3.2 术语提取规则

1. **提取范围**: 国家名称、国家机关名称、党及国家领导人名称、企事业单位名称、各行业专业名词、公司及学校等机构名称
2. **输出格式**: 多个术语名词之间使用空格符隔开，如 `"中国 中科大"`
3. **重要约束**:
   - 只提取原始术语词汇，不需要进行任何翻译操作
   - 不需要进行任何别名映射操作

#### 3.3.3 向量数据库操作流程

```
1. 提取术语词汇
2. 调用CommonTools.embedding_search_for_terms查询
3. 如果相似度<0.85，调用QdrantOperationTools.embedding_term_and_save保存
4. 返回JSON格式的结果（包含terms和term_count）
```

**返回格式**:
```json
{"terms":"重庆大学 中科大","term_count":2}
```

---

### 3.4 SQL操作服务（NL2SQLService）

**文件位置**:
- Prompt: `src/main/resources/database-operation-prompt.txt`
- 服务类: `src/main/java/org/fb/service/impl/NL2SQLService.java`

#### 3.4.1 Prompt设计

**角色定义**: MySQL数据库专家

#### 3.4.2 SQL生成规则

```
1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE等修改数据的语句
2. 确保SQL语句中的所有字段和表名都存在于提供的数据库结构中
3. 绝对不要使用'default'、'null'、'DEFAULT'、'NULL'等作为字符串字面值
4. 对于字符串字段，使用单引号包裹值，但值必须是实际的数据内容
5. 对于数值字段，不要使用引号，直接使用数字
6. 对于日期字段，使用标准的日期格式，如'2024-01-01'
7. 检查生成的SQL，确保WHERE条件中的值与字段类型匹配
```

#### 3.4.3 执行流程

```
1. 根据用户输入的自然语言查询，生成对应的SQL SELECT语句
2. 清理SQL语句（移除AI返回的markdown代码块标记）
3. 验证SQL语句的合法性
4. 检查SQL是否包含危险操作（DROP、DELETE、UPDATE、INSERT等）
5. 验证SQL中的类型匹配
6. 执行SQL查询
7. 返回查询结果
```

#### 3.4.4 SQL清理逻辑

**问题背景**: AI可能返回带markdown代码块的SQL，如：
```sql
SELECT * FROM appointment WHERE doctor_name = '王源环';
```

**清理方案**:
```java
private String cleanSQL(String rawSQL) {
    // 移除 ```sql 和 ``` 标记
    cleaned = cleaned.replaceAll("(?m)^\\s*```sql\\s*", "");
    cleaned = cleaned.replaceAll("(?m)^\\s*```\\s*", "");
    cleaned = cleaned.replaceAll("(?m)\\s*```\\s*$", "");
    cleaned = cleaned.replaceAll("```sql", "");
    cleaned = cleaned.replaceAll("```", "");
    // 清理首尾空白和换行
    cleaned = cleaned.trim();
    return cleaned;
}
```

---

### 3.5 默认助手（ChatAssistant）

**文件位置**:
- Prompt: `src/main/resources/default-prompt.txt`
- Agent接口: `src/main/java/org/fb/service/assistant/ChatAssistant.java`

#### 3.5.1 Prompt设计

**角色定义**: 拥有本地知识库、通用知识等多种类型数据的个人知识助手

#### 3.5.2 知识来源优先级

```
1. 本地数据库中已存在的相关知识
2. 大模型已知的通用类型知识
3. 网络知识
```

#### 3.5.3 可用工具

| 工具名称 | 功能描述 |
|---------|---------|
| `NaturalLanguageSQLAgent.doSQL()` | MySQL数据库操作 |
| `MongoDBTools.mongoDbSearch()` | MongoDB数据操作 |
| `CommonTools.embeddingSearch()` | Qdrant向量数据库检索 |
| `PersonalDataTools.searchPersonalResume()` | 个人简历信息查询 |
| `PersonalDataTools.getAllPersonalInfo()` | 获取所有个人信息 |

#### 3.5.4 重要提示

```
- 当用户询问特定知识、专业术语、文档内容等问题时，优先使用qdrant向量数据库检索
- 向量数据库检索会返回多条匹配结果，请综合所有结果进行分析和回答
- 如果向量数据库检索结果为空或相似度较低，再考虑使用其他数据源或通用知识回答
```

---

## 四、核心类与接口清单

### 4.1 Assistant接口类

| 类名 | 职责 | 使用的Prompt |
|------|------|-------------|
| `DoctorAgent` | 医疗助手，处理预约、查询等医疗相关业务 | doctorAgent-prompt-template.txt |
| `TranslaterService` | 翻译助手，处理多语言翻译 | translate-prompt.txt |
| `TermExtractionAgent` | 术语提取助手，提取专业术语 | termExtractionAgent-prompt-template.txt |
| `ChatAssistant` | 默认助手，处理通用对话 | default-prompt.txt |
| `ChatTypeAssistant` | 意图识别助手，判断用户意图 | chat-type-prompt.txt |
| `NaturalLanguageSQLAgent` | SQL转换助手，生成SQL语句 | database-operation-prompt.txt |

### 4.2 工具类

| 类名 | 工具名称 | 功能描述 |
|------|---------|---------|
| `AppointmentTools` | book_appointment | 预约挂号 |
| `AppointmentTools` | cancel_appointment | 取消预约 |
| `AppointmentTools` | query_department | 查询号源 |
| `AppointmentTools` | query_info | 查询患者挂号信息 |
| `AppointmentTools` | query_doctor_appointments | 查询医生预约列表 |
| `BaiduMapMcpAssistant` | baidu_map_mcp | 百度地图导航 |

### 4.3 服务实现类

| 类名 | 职责 |
|------|------|
| `ChatServiceImpl` | 核心聊天服务，实现意图识别和路由分发 |
| `NL2SQLService` | SQL操作服务，处理自然语言转SQL查询 |
| `AppointmentServiceImpl` | 预约服务，提供预约数据的数据库操作 |

---

## 五、配置与部署

### 5.1 启动配置

**主应用类**: `RAGTranslationApplication.java`

```java
@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@MapperScan("mapper")
@EnableDiscoveryClient
@RefreshScope
public class RAGTranslationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RAGTranslationApplication.class, args);
    }
}
```

### 5.2 数据库配置

**配置文件 `src/main/resources**:/application-docker.yml`

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://mysql:3306/mydocker?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
```

### 5.3 打包部署

**打包脚本**: `deploy-desktop.bat`

支持两种启动方式：
1. `deploy-desktop.bat run` - 直接运行
2. `deploy-desktop.bat` - 启动并打开浏览器访问

---

## 六、总结

### 6.1 设计亮点

1. **模块化设计**: 每个业务场景对应独立的Prompt和Assistant，便于维护和扩展
2. **意图识别机制**: 通过AI模型自动识别用户意图，实现智能路由
3. **工具调用规范**: 严格强调必须通过工具访问数据库，禁止直接生成回复
4. **预判断逻辑**: 在SQL操作前增加预判断，避免误识别
5. **SQL清理机制**: 清理AI返回的markdown标记，确保SQL可执行

### 6.2 优化建议

1. **工具调用监控**: 增加工具调用日志记录，便于问题排查
2. **错误处理增强**: 对工具调用失败的情况提供更友好的错误提示
3. **性能优化**: 对高频查询增加缓存机制
4. **测试覆盖**: 增加各业务场景的单元测试和集成测试

---

**文档版本**: 1.0
**最后更新**: 2026年2月
**维护者**: AI Development Team
