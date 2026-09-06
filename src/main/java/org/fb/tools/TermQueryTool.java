package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 纯术语查询工具
 * 仅用于RAG检索场景的术语查询，不涉及翻译功能
 * 与翻译工具分离，避免误触发翻译
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermQueryTool {
    
    private static final Logger log = LoggerFactory.getLogger(TermQueryTool.class);

    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private final EmbeddingModel embeddingModel;

    @Qualifier("qdrantEmbeddingStore")
    private final dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 查询术语（仅用于RAG检索，不翻译）
     * 返回术语的中文含义和相关解释
     * 
     * 使用场景：
     * - 用户询问某个术语的含义
     * - 需要解释专业词汇
     * - RAG检索中的术语解析
     * 
     * 严禁用于翻译场景！
     */
    @Tool(name = "query_term_meaning", value = "查询术语含义（仅用于RAG检索，不翻译）：根据术语名称返回其中文含义、解释和相关概念")
    public String queryTermMeaning(@P(value = "term", required = true) String term) {
        log.info("========== 纯术语查询工具开始 ==========");
        log.info("查询术语: {}", term);

        try {
            // 1. 向量检索
            var queryEmbedding = embeddingModel.embed(term).content();
            var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(5)
                    .minScore(0.6)
                    .build();
            
            var searchResults = embeddingStore.search(searchRequest);
            
            if (searchResults.matches().isEmpty()) {
                log.info("未找到相关术语");
                log.info("========== 纯术语查询工具完成 ==========");
                return "未找到术语'" + term + "'的相关信息";
            }

            // 2. 提取术语信息
            StringBuilder result = new StringBuilder();
            result.append("术语：").append(term).append("\n");
            
            Set<String> uniqueTerms = new HashSet<>();
            for (var match : searchResults.matches()) {
                String termInfo = match.embedded().text();
                if (!uniqueTerms.contains(termInfo)) {
                    uniqueTerms.add(termInfo);
                    result.append("- ").append(termInfo).append("\n");
                }
            }

            log.info("找到{}个相关术语", uniqueTerms.size());
            log.info("========== 纯术语查询工具完成 ==========");
            
            return result.toString();

        } catch (Exception e) {
            log.error("术语查询失败: {}", e.getMessage(), e);
            return "查询术语'" + term + "'时发生错误: " + e.getMessage();
        }
    }

    /**
     * 批量查询术语（仅用于RAG检索）
     */
    @Tool(name = "query_multiple_terms", value = "批量查询术语含义（仅用于RAG检索）：根据多个术语名称返回其含义和解释")
    public String queryMultipleTerms(@P(value = "terms", required = true) List<String> terms) {
        log.info("批量查询术语: {}", terms);
        
        StringBuilder result = new StringBuilder();
        for (String term : terms) {
            String termResult = queryTermMeaning(term);
            result.append(termResult).append("\n\n");
        }
        
        return result.toString();
    }

    /**
     * 术语相关性检查
     * 检查输入文本中是否包含相关术语，返回匹配的术语列表
     */
    @Tool(name = "find_related_terms", value = "查找相关术语：在输入文本中查找相关术语，返回术语列表和含义（仅用于RAG检索）")
    public String findRelatedTerms(@P(value = "text", required = true) String text) {
        log.info("在文本中查找相关术语: {}", text);

        try {
            // 1. 向量检索
            var queryEmbedding = embeddingModel.embed(text).content();
            var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(10)
                    .minScore(0.5)
                    .build();
            
            var searchResults = embeddingStore.search(searchRequest);
            
            if (searchResults.matches().isEmpty()) {
                log.info("未找到相关术语");
                return "文本中未找到相关术语";
            }

            // 2. 提取术语并检查是否在文本中出现
            List<String> foundTerms = new ArrayList<>();
            Set<String> uniqueTerms = new HashSet<>();
            
            for (var match : searchResults.matches()) {
                String termInfo = match.embedded().text();
                // 提取术语名称
                String termName = extractTermName(termInfo);
                
                if (termName != null && text.contains(termName) && !uniqueTerms.contains(termName)) {
                    uniqueTerms.add(termName);
                    foundTerms.add(termName);
                }
            }

            if (foundTerms.isEmpty()) {
                log.info("文本中未包含检索到的术语");
                return "文本中未包含相关术语";
            }

            // 3. 返回结果
            StringBuilder result = new StringBuilder();
            result.append("在文本中发现以下相关术语：\n");
            for (String foundTerm : foundTerms) {
                result.append("- ").append(foundTerm).append("\n");
            }

            log.info("发现{}个相关术语", foundTerms.size());
            return result.toString();

        } catch (Exception e) {
            log.error("查找相关术语失败: {}", e.getMessage(), e);
            return "查找相关术语时发生错误: " + e.getMessage();
        }
    }

    /**
     * 从存储的术语信息中提取术语名称
     */
    private String extractTermName(String termInfo) {
        try {
            if (termInfo.contains("terms")) {
                // JSON格式: {"terms":"火中取栗","term_count":1}
                int start = termInfo.indexOf("\"terms\":\"") + "\"terms\":\"".length();
                int end = termInfo.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return termInfo.substring(start, end);
                }
            }
            return termInfo.split("[,，；;\\.\\s]")[0]; // 取第一个词
        } catch (Exception e) {
            return termInfo;
        }
    }
}