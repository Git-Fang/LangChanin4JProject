package org.fb.matcher;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fb.model.TermMatchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维度术语匹配引擎
 * 整合语义、关键词、拼音、字符四种匹配方式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiDimensionalTermMatcher {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final KeywordMatcher keywordMatcher;
    private final PinyinMatcher pinyinMatcher;
    private final CharacterMatcher characterMatcher;
    private final PriorityFusion priorityFusion;

    @Value("${term.match.threshold.semantic:0.7}")
    private double semanticThreshold;

    @Value("${term.match.threshold.final:0.6}")
    private double finalThreshold;

    /**
     * 多维度术语匹配主方法
     */
    public List<TermMatchResult> findMatchingTerms(String text) {
        log.info("========== 多维度术语匹配开始 ==========");
        log.info("输入文本: {}", text);

        // 并行执行多维度匹配
        List<TermMatchResult> allResults = new ArrayList<>();

        try {
            // 1. 语义匹配（向量检索）
            List<TermMatchResult> semanticMatches = semanticMatch(text);
            log.info("语义匹配结果: {} 个", semanticMatches.size());
            allResults.addAll(semanticMatches);

            // 2. 关键词匹配（需要术语库）
            List<TermMatchResult> keywordMatches = keywordMatch(text);
            log.info("关键词匹配结果: {} 个", keywordMatches.size());
            allResults.addAll(keywordMatches);

            // 3. 拼音匹配（需要术语库）
            List<TermMatchResult> pinyinMatches = pinyinMatch(text);
            log.info("拼音匹配结果: {} 个", pinyinMatches.size());
            allResults.addAll(pinyinMatches);

            // 4. 字符匹配（需要术语库）
            List<TermMatchResult> characterMatches = characterMatch(text);
            log.info("字符匹配结果: {} 个", characterMatches.size());
            allResults.addAll(characterMatches);

            // 5. 融合所有匹配结果
            List<TermMatchResult> fusedResults = priorityFusion.fuseResults(allResults, finalThreshold);
            log.info("融合后结果: {} 个", fusedResults.size());

            log.info("========== 多维度术语匹配完成 ==========");
            return fusedResults;

        } catch (Exception e) {
            log.error("多维度术语匹配失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 语义匹配（向量检索）
     */
    private List<TermMatchResult> semanticMatch(String text) {
        try {
            EmbeddingSearchResult<TextSegment> searchResult = getMatchWordsForTerms(text);

            if (searchResult.matches().isEmpty()) {
                return Collections.emptyList();
            }

            return searchResult.matches().stream()
                .filter(match -> match.score() >= semanticThreshold)
                .map(this::convertToSemanticMatch)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("语义匹配失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 关键词匹配（简化实现，实际需要术语库）
     */
    private List<TermMatchResult> keywordMatch(String text) {
        // 这里简化处理，实际需要从数据库或向量库获取所有术语进行匹配
        // 暂时返回空列表，后续实现术语库集成
        return Collections.emptyList();
    }

    /**
     * 拼音匹配（简化实现，实际需要术语库）
     */
    private List<TermMatchResult> pinyinMatch(String text) {
        // 这里简化处理，实际需要从数据库或向量库获取所有术语进行匹配
        // 暂时返回空列表，后续实现术语库集成
        return Collections.emptyList();
    }

    /**
     * 字符匹配（简化实现，实际需要术语库）
     */
    private List<TermMatchResult> characterMatch(String text) {
        // 这里简化处理，实际需要从数据库或向量库获取所有术语进行匹配
        // 暂时返回空列表，后续实现术语库集成
        return Collections.emptyList();
    }

    /**
     * 获取术语向量匹配结果
     */
    private EmbeddingSearchResult<TextSegment> getMatchWordsForTerms(String question) {
        var queryEmbedding = embeddingModel.embed(question).content();
        
        // 创建术语类型过滤器
        Filter typeFilter = MetadataFilterBuilder.metadataKey("type").isEqualTo("TERMS");

        var searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(30)
                .minScore(0.6)
                .filter(typeFilter)
                .build();

        return embeddingStore.search(searchRequest);
    }

    /**
     * 转换为语义匹配结果
     */
    private TermMatchResult convertToSemanticMatch(EmbeddingMatch<TextSegment> match) {
        String matchedText = match.embedded().text();
        String actualTerm = parseTermFromJson(matchedText);

        return TermMatchResult.builder()
                .term(actualTerm)
                .semanticScore(match.score())
                .matchType("semantic")
                .matchedText(matchedText)
                .priority("medium")  // 默认优先级
                .build();
    }

    /**
     * 从JSON解析术语
     */
    private String parseTermFromJson(String json) {
        // 简化实现，实际应使用JSON解析库
        try {
            if (json.contains("\"term\":\"")) {
                int start = json.indexOf("\"term\":\"") + 8;
                int end = json.indexOf("\"", start);
                if (end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", e.getMessage());
        }
        return json;
    }

    /**
     * 缓存匹配结果
     */
    @Cacheable(value = "termMatches", key = "#text.hashCode()")
    public List<TermMatchResult> findMatchingTermsCached(String text) {
        return findMatchingTerms(text);
    }
}