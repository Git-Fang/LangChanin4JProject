package org.fb.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.fb.bean.RagQueryResult;
import org.fb.config.QdrantConfig;
import org.fb.service.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG查询服务实现
 * 支持向量相似度搜索和基于LLM的答案生成
 */
@Service
public class RagQueryServiceImpl implements RagQueryService {
    private static final Logger log = LoggerFactory.getLogger(RagQueryServiceImpl.class);

    /**
     * 默认最大返回结果数
     */
    private static final int DEFAULT_MAX_RESULTS = 5;

    /**
     * 默认最低相似度分数阈值
     */
    private static final double DEFAULT_MIN_SCORE = 0.5;

    @Autowired
    @Qualifier("qwenChatModel")
    private ChatModel chatModel;

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("qdrantEmbeddingStore")
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private QdrantConfig qdrantConfig;

    @Value("${rag.prompt-template:rag-prompt}")
    private String ragPromptTemplatePath;

    @Value("${rag.max-context-chunks:3}")
    private int maxContextChunks;

    private String ragPromptTemplate;

    public RagQueryServiceImpl() {
        initPromptTemplate();
    }

    /**
     * 初始化RAG提示词模板
     */
    private void initPromptTemplate() {
        try {
            org.springframework.core.io.ClassPathResource resource = 
                    new org.springframework.core.io.ClassPathResource(ragPromptTemplatePath + ".txt");
            if (resource.exists()) {
                try (java.io.InputStream is = resource.getInputStream()) {
                    ragPromptTemplate = new String(is.readAllBytes(), "UTF-8");
                    log.info("成功加载RAG提示词模板");
                }
            } else {
                log.warn("RAG提示词模板文件不存在，使用默认模板");
                ragPromptTemplate = getDefaultRagPromptTemplate();
            }
        } catch (Exception e) {
            log.warn("加载RAG提示词模板失败，使用默认模板: {}", e.getMessage());
            ragPromptTemplate = getDefaultRagPromptTemplate();
        }
    }

    /**
     * 获取默认RAG提示词模板
     */
    private String getDefaultRagPromptTemplate() {
        return "你是一个专业的文档问答助手。请根据以下参考内容回答用户的问题。\n\n" +
                "【参考内容】\n" +
                "{{context}}\n\n" +
                "【用户问题】\n" +
                "{{question}}\n\n" +
                "请根据参考内容回答问题，如果参考内容中没有相关信息，请说明无法从提供的内容中找到答案。\n\n" +
                "【重要】请使用Markdown格式组织你的回答：\n" +
                "1. 使用 ## 标题格式作为主要结构\n" +
                "2. 使用 **加粗** 强调关键信息\n" +
                "3. 使用有序列表(1. 2. 3.)或无序列表(- )列出要点\n" +
                "4. 如需引用参考内容，使用 > 引用格式\n" +
                "5. 保持简洁，段落长度适中";
    }

    @Override
    public RagQueryResult query(String query) {
        return query(query, DEFAULT_MAX_RESULTS, DEFAULT_MIN_SCORE, true);
    }

    @Override
    public RagQueryResult query(String query, int maxResults, double minScore, boolean generateAnswer) {
        long startTime = System.currentTimeMillis();
        log.info("开始RAG查询: {}, 最大结果数: {}, 最低分数: {}, 生成答案: {}", 
                query, maxResults, minScore, generateAnswer);

        try {
            // 执行向量相似度搜索
            RagQueryResult searchResult = searchOnly(query, maxResults, minScore);
            
            if (searchResult.getRelevantChunks().isEmpty()) {
                log.info("未找到相关的文档切片");
                searchResult.setQueryTimeMs(System.currentTimeMillis() - startTime);
                return searchResult;
            }

            // 如果需要生成答案
            if (generateAnswer && !searchResult.getRelevantChunks().isEmpty()) {
                String answer = generateAnswerWithLlm(query, searchResult.getRelevantChunks());
                searchResult.setAnswer(answer);
                searchResult.setGeneratedWithLlm(true);
            }

            searchResult.setQueryTimeMs(System.currentTimeMillis() - startTime);
            log.info("RAG查询完成，找到 {} 条相关结果，耗时 {}ms", 
                    searchResult.getMatchedCount(), searchResult.getQueryTimeMs());

            return searchResult;

        } catch (Exception e) {
            log.error("RAG查询失败: {}", e.getMessage(), e);
            RagQueryResult errorResult = new RagQueryResult();
            errorResult.setQueryTimeMs(System.currentTimeMillis() - startTime);
            errorResult.setAnswer("查询失败: " + e.getMessage());
            errorResult.setGeneratedWithLlm(false);
            return errorResult;
        }
    }

    @Override
    public RagQueryResult searchOnly(String query, int maxResults, double minScore) {
        log.debug("执行向量相似度搜索: {}", query);

        try {
            // 1. 将查询文本转换为向量
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            log.debug("查询向量生成完成");

            // 2. 执行相似度搜索
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
            log.debug("向量搜索完成，找到 {} 条匹配结果", matches.size());

            // 3. 转换结果
            List<RagQueryResult.RelevantChunk> relevantChunks = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                
                // 从metadata中提取信息（处理不同类型）
                String articleTitle = getMetadataAsString(segment, "articleTitle");
                String summary = getMetadataAsString(segment, "summary");
                int chunkIndex = getMetadataAsInt(segment, "chunkIndex");
                
                // 如果没有metadata，从text中解析（兼容旧格式）
                if (articleTitle == null || summary == null) {
                    String[] parts = parseStoredContent(segment.text());
                    articleTitle = parts[0];
                    summary = parts[1];
                }

                RagQueryResult.RelevantChunk chunk = new RagQueryResult.RelevantChunk(
                        articleTitle != null ? articleTitle : "未知文档",
                        summary != null ? summary : "",
                        segment.text(),
                        match.score(),
                        chunkIndex
                );
                relevantChunks.add(chunk);
            }

            // 4. 构建结果
            RagQueryResult result = new RagQueryResult();
            result.setRelevantChunks(relevantChunks);
            result.setMatchedCount(relevantChunks.size());
            result.setGeneratedWithLlm(false);

            log.info("向量搜索完成，找到 {} 条相关结果", relevantChunks.size());
            return result;

        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            RagQueryResult errorResult = new RagQueryResult();
            errorResult.setRelevantChunks(new ArrayList<>());
            errorResult.setMatchedCount(0);
            return errorResult;
        }
    }

    /**
     * 使用LLM生成答案
     */
    private String generateAnswerWithLlm(String question, List<RagQueryResult.RelevantChunk> relevantChunks) {
        log.debug("开始使用LLM生成答案，上下文切片数: {}", relevantChunks.size());

        try {
            // 构建上下文
            StringBuilder contextBuilder = new StringBuilder();
            int chunksToUse = Math.min(relevantChunks.size(), maxContextChunks);
            
            for (int i = 0; i < chunksToUse; i++) {
                RagQueryResult.RelevantChunk chunk = relevantChunks.get(i);
                contextBuilder.append(String.format("【文档 %d】\n", i + 1));
                contextBuilder.append("标题: ").append(chunk.getArticleTitle()).append("\n");
                contextBuilder.append("摘要: ").append(chunk.getSummary()).append("\n");
                contextBuilder.append("内容: ").append(chunk.getContent()).append("\n\n");
            }

            // 替换模板中的占位符
            String prompt = ragPromptTemplate
                    .replace("{{context}}", contextBuilder.toString())
                    .replace("{{question}}", question);

            log.debug("RAG提示词构建完成，长度: {}", prompt.length());

            // 调用LLM生成答案
            String answer = chatModel.chat(prompt);
            
            // 清理答案
            answer = answer.trim();
            if (answer.startsWith("\"") && answer.endsWith("\"")) {
                answer = answer.substring(1, answer.length() - 1);
            }
            if (answer.startsWith("'") && answer.endsWith("'")) {
                answer = answer.substring(1, answer.length() - 1);
            }

            log.info("LLM答案生成完成，答案长度: {} 字符", answer.length());
            return answer;

        } catch (Exception e) {
            log.error("LLM答案生成失败: {}", e.getMessage(), e);
            return "答案生成失败: " + e.getMessage();
        }
    }

    /**
     * 安全获取metadata中的字符串值
     * 处理不同类型的存储值
     */
    private String getMetadataAsString(TextSegment segment, String key) {
        try {
            Map<String, Object> map = segment.metadata().toMap();
            Object value = map.get(key);
            if (value == null) {
                return null;
            }
            return value.toString();
        } catch (Exception e) {
            log.debug("获取metadata '{}' 失败: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 安全获取metadata中的整数值为int类型
     * 处理Long、Integer、String等不同类型
     */
    private int getMetadataAsInt(TextSegment segment, String key) {
        try {
            Map<String, Object> map = segment.metadata().toMap();
            Object value = map.get(key);
            if (value == null) {
                return 0;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.debug("获取metadata '{}' 作为整数失败: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * 解析存储的内容格式
     * 格式：【文章标题】xxx | 【段落摘要】zzz | 【切片内容】xxx
     */
    private String[] parseStoredContent(String content) {
        String[] result = new String[]{"未知文档", ""};
        
        if (content == null || content.isEmpty()) {
            return result;
        }

        try {
            // 提取文章标题
            int titleStart = content.indexOf("【文章标题】");
            int titleEnd = content.indexOf("|", titleStart);
            if (titleStart >= 0 && titleEnd > titleStart) {
                result[0] = content.substring(titleStart + 6, titleEnd).trim();
            }

            // 提取段落摘要
            int summaryStart = content.indexOf("【段落摘要】");
            int summaryEnd = content.indexOf("|", summaryStart);
            if (summaryStart >= 0 && summaryEnd > summaryStart) {
                result[1] = content.substring(summaryStart + 6, summaryEnd).trim();
            }
        } catch (Exception e) {
            log.warn("解析存储内容失败: {}", e.getMessage());
        }

        return result;
    }
}
