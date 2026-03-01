# RAG 知识库检索召回率优化方案

## 一、问题背景

### 1.1 问题描述

当前项目在进行涉及简历、过往项目等相关内容的回答时，Qdrant 向量数据库中存在相关数据，但系统回复内容却是"未找到相关数据"或进行"胡编乱造"。系统并不能正确地进行知识库检索，或在检索到内容的基础上进行总结性回复。

### 1.2 影响范围

- 个人简历查询（如"方彪的简历是什么？"）
- 项目经验查询（如"XXX的项目经验"）
- 工作经历查询（如"XXX的工作经历"）
- 综合知识库查询（如"结合知识库总结XXX"）

---

## 二、系统架构分析

### 2.1 当前 RAG 架构

```
用户问题 → ChatTypeAssistant(意图识别) → ChatServiceImpl 
                                              ↓
                                    [意图类型路由]
                                              ↓
    ┌────────────────────────────────────────────────────┐
    │ medical → DoctorAgent                              │
    │ translation → TranslaterService                   │
    │ term_extraction → TermExtractionAgent              │
    │ sql_transfer → NL2SQLService                      │
    │ general → ChatAssistant (有知识库工具!)            │
    └────────────────────────────────────────────────────┘
                                              ↓
                                   ChatAssistant.chat()
                                              ↓
                    ┌───────────────────────────────┐
                    │ tools:                        │
                    │ - knowledgeBaseRetrievalService
                    │ - commonTools.embeddingSearch  │
                    │ - MongoDBTools                │
                    │ - PersonalDataTools          │
                    └───────────────────────────────┘
```

### 2.2 知识库检索流程

`KnowledgeBaseRetrievalService.searchKnowledgeBase()` 按以下顺序检索：

1. **MySQL数据库** → NL2SQLService
2. **MongoDB数据库** → MongoDBTools
3. **Qdrant向量数据库** → CommonTools.embeddingSearch()

### 2.3 核心组件说明

| 组件 | 文件 | 功能 |
|------|------|------|
| ChatTypeAssistant | `ChatTypeAssistant.java` | 意图识别，确定用户问题类型 |
| ChatServiceImpl | `ChatServiceImpl.java` | 意图路由，分发到不同业务处理 |
| ChatAssistant | `ChatAssistant.java` | 主对话服务，包含知识库工具 |
| KnowledgeBaseRetrievalService | `KnowledgeBaseRetrievalService.java` | 知识库综合检索 |
| CommonTools | `CommonTools.java` | 向量检索核心实现 |

---

## 三、根因分析

### 3.1 根因 1：意图识别缺失 RAG 类型 🔴

**问题位置**：`chat-type-prompt.txt`

**问题描述**：
- 意图类型只有：`sql_transfer`, `medical`, `translation`, `term_extraction`, `general`
- 没有 `rag_retrieval` 类型！

**后果**：
- 用户问"请结合知识库总结方彪的过往项目经验"
- 被识别为 `general` 类型
- 虽然 `ChatAssistant` 有知识库工具，但 LLM **不一定会主动调用**
- LLM 可能直接根据"通用知识"编造答案

---

### 3.2 根因 2：Embedding 检索参数不当 🔴

**问题位置**：`CommonTools.getMatchWords()` 第 117-121 行

```java
// 原代码
EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(30)
    .minScore(0.1)  // ⚠️ 问题：0.1 太低！
    .build();
```

**后果**：
- 相似度 0.1 以上的文档都会被返回
- 大量噪声数据淹没真正相关的内容
- LLM 难以从噪声中提取有效信息

---

### 3.3 根因 3：Embedding 模型对中文支持不佳 🔴

**问题位置**：`LLMConfig.java` 第 242-245 行

```java
@Bean(name = "allMiniLmL6V2EmbeddingModel")
public EmbeddingModel allMiniLmL6V2EmbeddingModel() {
    return new AllMiniLmL6V2EmbeddingModel();  // ⚠️ 英文为主，中文效果差
}
```

**后果**：
- 中文简历、项目描述的向量化效果差
- 与 Qdrant 中已存储的中文向量匹配度低

---

### 3.4 根因 4：查询词提取逻辑过于简单 🟡

**问题位置**：`KnowledgeBaseRetrievalService.extractSearchKeywords()` 第 184-229 行

```java
// 原代码 - 只提取 2-4 个字符的中文词
Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}");
```

**后果**：
- "请结合知识库总结方彪的过往项目经验" 
- 可能只提取到 "项目经验"
- 丢失关键人名 "方彪"

---

### 3.5 根因 5：文档分块策略不适配长文档 🟡

**问题位置**：`QdrantOperationTools.parseAndEmbeddingForTerms()` 第 114 行

```java
DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(800, 80);
```

**后果**：
- 简历等长文档被 800 字符强制切分
- 语义被破坏，如项目经历被截断

---

### 3.6 根因 6：LLM 幻觉问题 🟡

**问题位置**：`default-prompt.txt` 虽有禁止幻觉规则，但执行不到位

**后果**：
- 当 `knowledge_base_search` 返回"暂无相关数据"时
- LLM 可能忽略提示，直接编造答案

---

## 四、解决方案

### 4.1 方案总览

| 优先级 | 方案 | 预期效果 | 实施状态 |
|--------|------|----------|----------|
| **P0** | 修复意图识别 | 显著提高召回率 | ✅ 已完成 |
| **P0** | 优化 Embedding 参数 | 过滤噪声数据 | ✅ 已完成 |
| **P1** | 改进查询词提取 | 更精准的检索 | ✅ 已完成 |
| **P1** | 增强 LLM 幻觉约束 | 减少编造答案 | ✅ 已完成 |
| **P1** | 添加多路召回策略 | 进一步提高召回 | ✅ 已完成 |
| **P2** | 更换中文 Embedding 模型 | 大幅提升中文匹配度 | ⏳ 待实施 |
| **P2** | 优化文档分块 | 保持语义完整 | ⏳ 待实施 |

---

### 4.2 P0 方案实施

#### 4.2.1 修复意图识别 - 添加 rag_retrieval 类型

**修改文件**：`src/main/resources/chat-type-prompt.txt`

```yaml
- rag_retrieval: **涉及需要查询知识库内容的问题**，即用户要求结合已有知识库数据回答。对应关键词包括但不限于：
    * "结合知识库"、"基于知识库"、"参考文档"、"根据文档"
    * "XXX的简历"、"XXX的项目经验"、"XXX的工作经历"、"XXX的过往"
    * "过往项目"、"历史项目"、"曾经任职"、"工作经历"
    * "个人介绍"、"自我介绍"、"简历"
    * "总结XXX"、"介绍一下XXX"
    * 英文："based on knowledge", "resume of", "project experience", "past projects"
    **重要**：只要用户询问需要从已有知识库（MySQL/MongoDB/Qdrant）中查询的信息，都属于 rag_retrieval 类型。
```

**修改文件**：`src/main/java/org/fb/service/impl/ChatServiceImpl.java`

```java
// 添加 KnowledgeBaseRetrievalService 注入
@Autowired
private KnowledgeBaseRetrievalService knowledgeBaseRetrievalService;

// 在 processByUserMeanings 方法中添加 rag_retrieval 处理
if (BusinessConstant.RAG_RETRIEVAL_TYPE.equals(intent)) {
    log.info("识别为 RAG 知识库检索类型，直接调用知识库检索服务");
    String result = knowledgeBaseRetrievalService.searchKnowledgeBase(userMessage);
    saveChatInfo(memoryId, userMessage, BusinessConstant.RAG_RETRIEVAL_TYPE);
    return result;
}
```

---

#### 4.2.2 优化 Embedding 检索参数

**修改文件**：`src/main/java/org/fb/tools/CommonTools.java`

```java
public EmbeddingSearchResult<TextSegment> getMatchWords(String question) {
    Embedding queryEmbedding = embeddingModel.embed(question).content();

    // 【优化】提高 minScore 阈值，减少噪声数据干扰
    // 原值 0.1 过低，导致大量不相关文档被召回
    // 调整原因：all-MiniLM-L6-v2 模型对中文支持有限，需要更高阈值过滤噪声
    EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(30)
            .minScore(0.6)  // 从 0.1 提升到 0.6，过滤低相关度结果
            .build();

    return embeddingStore.search(searchRequest);
}
```

---

### 4.3 P1 方案实施

#### 4.3.1 改进查询词提取逻辑

**修改文件**：`src/main/java/org/fb/service/KnowledgeBaseRetrievalService.java`

```java
private String extractSearchKeywords(String query) {
    if (query == null || query.isEmpty()) {
        return query;
    }
    
    log.info("开始提取检索关键词，原Query: {}", query);
    
    StringBuilder keywords = new StringBuilder();
    
    // 1. 提取人名（扩展匹配模式）
    String[] namePatterns = {"方彪", "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十", 
                              "刘一", "刘二", "刘三", "陈一", "陈二", "杨一", "杨二"};
    
    String extractedName = null;
    for (String name : namePatterns) {
        if (query.contains(name)) {
            extractedName = name;
            keywords.append(name);
            break;
        }
    }
    
    // 2. 提取核心实体（项目、技术、公司等）
    List<String> entities = new ArrayList<>();
    
    // 项目相关
    if (query.contains("项目") || query.contains("经验") || query.contains("经历")) {
        entities.add("项目经验");
        entities.add("项目经历");
    }
    // 工作相关
    if (query.contains("工作") || query.contains("任职") || query.contains("职位")) {
        entities.add("工作经历");
        entities.add("任职");
    }
    // 简历相关
    if (query.contains("简历") || query.contains("个人") || query.contains("介绍")) {
        entities.add("简历");
        entities.add("个人");
    }
    // 技能相关
    if (query.contains("技能") || query.contains("技术") || query.contains("能力")) {
        entities.add("技能");
        entities.add("技术");
    }
    
    // 3. 构建优化后的查询词
    if (extractedName != null) {
        // 优先使用"人名 + 实体"组合
        if (!entities.isEmpty()) {
            keywords.append(" ").append(entities.get(0));
        }
    } else {
        // 如果没有提取到人名，提取query中最有意义的中文词汇
        // ... (省略详细逻辑)
    }
    
    return result;
}
```

---

#### 4.3.2 增强 LLM 幻觉约束

**修改文件**：`src/main/resources/default-prompt.txt`

```yaml
## 【禁止幻觉规则 - 最高优先级】

### 检索失败时的强制规则（必须严格遵守）

**情况A：所有知识库都无结果**
```
抱歉，我在当前知识库中未找到关于[具体查询目标]的相关信息。

【已检索范围】
- MySQL数据库：❌ 无结果
- MongoDB数据库：❌ 无结果  
- Qdrant向量数据库：❌ 无结果

【建议】
1. 请确认该信息已导入知识库
2. 可尝试使用网络搜索获取更多信息
3. 如需补充此信息，请提供相关资料

【重要】未经核实的信息，我不能编造回答。
```

**情况B：部分知识库有结果，但不足以回答问题**
```
关于[查询目标]，我在知识库中找到了以下信息：
[找到的具体信息]

【未找到部分】
- [明确说明哪些信息未找到]

【重要】以上信息来自知识库检索，请勿添加未经核实的内容。
```

**情况C：检索到信息但相似度较低**
```
关于[查询目标]，我在知识库中找到以下参考信息（相似度较低，请核实）：

[找到的信息]

【相似度提示】
- 最高相似度：X%
- 请注意：相似度较低，内容可能不完全匹配
```

---

#### 4.3.3 添加多路召回策略

**修改文件**：`src/main/java/org/fb/service/KnowledgeBaseRetrievalService.java`

```java
private String searchFromQdrant(String query) {
    // 【新增】多路召回策略：尝试多个检索词
    List<String> searchQueries = generateMultipleSearchQueries(query, optimizedQuery);
    log.info("Qdrant检索 - 多路召回查询词: {}", searchQueries);
    
    // 用于去重和收集结果
    Set<String> dedup = new HashSet<>();
    List<String> allResults = new ArrayList<>();
    double maxScore = 0.0;
    
    // 多路召回执行
    for (String sq : searchQueries) {
        log.info("Qdrant检索 - 执行查询: {}", sq);
        String result = commonTools.embeddingSearch(sq);
        
        if (result != null && !result.contains("查无相关数据")) {
            // 解析相似度信息
            // ... 去重处理 ...
            allResults.add(...);
        }
    }
    
    // 返回合并后的结果
    // ...
}

/**
 * 生成多个检索查询词，实现多路召回
 */
private List<String> generateMultipleSearchQueries(String originalQuery, String optimizedQuery) {
    List<String> queries = new ArrayList<>();
    
    // 1. 首先添加优化后的查询词
    if (optimizedQuery != null && !optimizedQuery.isEmpty()) {
        queries.add(optimizedQuery);
    }
    
    // 2. 提取人名并生成多种组合查询
    // "方彪" → ["方彪", "方彪 项目", "方彪 工作", "方彪 简历", ...]
    
    // 3. 添加核心关键词变体
    // "项目经验" → ["项目经验", "工作项目", "过往项目"]
    
    // 4. 去重返回
    return new ArrayList<>(new LinkedHashSet<>(queries));
}
```

---

## 五、待实施优化方案

### 5.1 更换中文 Embedding 模型

**问题**：`all-MiniLM-L6-v2` 对中文支持有限

**方案 A - 使用阿里云 text-embedding-v3**：
```java
@Bean(name = "textEmbeddingModel")
public EmbeddingModel textEmbeddingModel() {
    return OpenAiEmbeddingModel.builder()
        .apiKey(dashscopeApiKey)
        .modelName("text-embedding-v3")
        .build();
}
```

**方案 B - 使用中文优化的本地模型**：
```java
@Bean(name = "bgeEmbeddingModel")
public EmbeddingModel bgeEmbeddingModel() {
    return new BgeSmallZhEmbeddingModel();  // 中文优化
}
```

**⚠️ 注意**：更换模型后需要**重新向量化**已存储的数据！

---

### 5.2 优化文档分块策略

**当前问题**：800 字符强制切分破坏语义

**优化方案**：
```java
// 方案 A：增大分块长度
DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(
    1500,  // 增大段落长度
    200,    // 重叠区域
    true    // 启用语义合并
);

// 方案 B：针对简历使用专用分块
if (fileName.contains("简历") || content.contains("项目经历")) {
    // 按"项目"、"工作经历"等标题切分
    splitter = new DocumentBySectionSplitter();
}
```

---

## 六、验证方案

### 6.1 测试用例设计

| 测试名称 | 输入 | 预期输出 |
|----------|------|----------|
| 人名检索 | "方彪的简历是什么？" | 检索到方彪的简历信息 |
| 项目经验检索 | "张三的项目经验" | 检索到张三的项目经历 |
| 工作经历检索 | "王五的工作经历" | 检索到王五的工作信息 |
| 综合查询 | "结合知识库总结李四的过往" | 检索并总结李四的相关信息 |
| 无结果处理 | "赵六的简历"（不存在） | 明确告知未找到 |

### 6.2 验证方法

1. **单元测试**：使用已知答案的问题验证检索结果
2. **日志审计**：检查 LLM 是否正确调用了工具
3. **端到端测试**：用真实简历数据测试完整流程

---

## 七、修改文件清单

| 序号 | 文件路径 | 修改内容 |
|------|----------|----------|
| 1 | `src/main/resources/chat-type-prompt.txt` | 添加 rag_retrieval 意图识别规则 |
| 2 | `src/main/resources/default-prompt.txt` | 强化禁止幻觉规则 |
| 3 | `src/main/java/org/fb/service/impl/ChatServiceImpl.java` | 添加 rag_retrieval 处理分支 |
| 4 | `src/main/java/org/fb/tools/CommonTools.java` | 优化 minScore 阈值 0.1→0.6 |
| 5 | `src/main/java/org/fb/service/KnowledgeBaseRetrievalService.java` | 改进查询词提取 + 多路召回 |

---

## 八、总结

### 8.1 核心改进

1. **意图识别**：新增 `rag_retrieval` 类型，确保知识库相关问题被正确路由
2. **检索参数**：提高相似度阈值，过滤低质量噪声
3. **查询优化**：智能提取人名+实体，构建多检索词
4. **结果合并**：多路召回策略，去重后返回
5. **幻觉约束**：强化 LLM 禁止编造规则

### 8.2 预期效果

- 知识库相关问题的召回率显著提升
- 减少 LLM 胡编乱造的情况
- 更精准的人名+项目组合检索
- 更好的中文语义匹配

### 8.3 后续优化建议

1. 更换中文优化的 Embedding 模型（如 text-embedding-v3 或 bge-small-zh）
2. 优化文档分块策略，保持语义完整性
3. 考虑添加 rerank 机制，对召回结果重排序
4. 添加检索结果置信度评估

---

*文档创建时间：2026年2月26日*
*项目：RAGTranslationApplication*
