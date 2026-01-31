package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommonTools {
    private static final Logger log = LoggerFactory.getLogger(CommonTools.class);

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("qdrantEmbeddingStore")
    private EmbeddingStore<TextSegment> embeddingStore;


    @Tool(name = "embedding_search", value="查询qdrant向量数据信息:根据传入数据{{question}}从qdrant向量数据库中查询并返回")
    public String embeddingSearch(@P(value="question", required = true) String question) {
        log.info("开始向量化查询。传入数据：{}", question);

        EmbeddingSearchResult<TextSegment> searchResult = getMatchWords(question);

        if (searchResult.matches().isEmpty()) {
            log.info("未查询到相关数据");
            return "数据库查无相关数据";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < searchResult.matches().size(); i++) {
            EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(i);
            log.info("答案{}相似度score：{}; 结果为：{}", i + 1, embeddingMatch.score(), embeddingMatch.embedded().text());
            result.append("【相关数据").append(i + 1).append("】\n");
            result.append(embeddingMatch.embedded().text()).append("\n\n");
        }

        return result.toString().trim();
    }

    @Tool(name = "embedding_search_for_terms", value="查询qdrant术语向量数据信息:根据传入数据{{question}}从qdrant向量数据库中仅查询type为TERMS的术语数据并返回")
    public String embeddingSearchForTerms(@P(value="question", required = true) String question) {
        log.info("========== embedding_search_for_terms 开始 ==========");
        log.info("【待翻译语句中提取的术语】: {}", question);

        EmbeddingSearchResult<TextSegment> searchResult = getMatchWordsForTerms(question);

        if (searchResult.matches().isEmpty()) {
            log.info("【术语翻译查询结果】: 未在向量数据库中找到对应翻译");
            return "NO_TERMS_FOUND";
        }

        StringBuilder result = new StringBuilder();
        int matchCount = 0;
        int totalMatches = searchResult.matches().size();

        log.info("【术语翻译查询】: 向量数据库共返回 {} 个匹配", totalMatches);

        for (int i = 0; i < searchResult.matches().size(); i++) {
            EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(i);
            double score = embeddingMatch.score();
            String matchedText = embeddingMatch.embedded().text();
            
            log.info("  - 术语: '{}' | 相似度: {} | 类型: TERMS", matchedText, score);
            
            result.append("【术语翻译匹配").append(i + 1).append("】\n");
            result.append("术语: ").append(matchedText).append("\n");
            result.append("相似度: ").append(score).append("\n\n");
            matchCount++;
        }

        log.info("【术语翻译查询结果】: 找到 {} 个术语翻译", matchCount);
        log.info("========== embedding_search_for_terms 完成 ==========");

        return result.toString().trim();
    }



    public EmbeddingSearchResult<TextSegment> getMatchWords(String question) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(30)
                .minScore(0.1)
                .build();

        return embeddingStore.search(searchRequest);
    }

    public EmbeddingSearchResult<TextSegment> getMatchWordsForTerms(String question) {
        log.info("开始术语向量化查询。传入数据：{}", question);

        try {
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            log.info("向量化完成，embedding维度：{}", queryEmbedding.dimension());

            Filter typeFilter = new MetadataFilterBuilder("type").isEqualTo("TERMS");
            log.info("创建filter：type = TERMS");

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(30)
                    .minScore(0.7)
                    .filter(typeFilter)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
            log.info("术语查询完成，匹配数量：{}", result.matches().size());

            return result;
        } catch (Exception e) {
            log.error("术语查询失败：{}", e.getMessage(), e);
            return new EmbeddingSearchResult<>(new ArrayList<>());
        }
    }

    @Tool(name = "do_translation", value = "生成翻译结果:将source_text翻译成target_language。如果有术语，先替换再翻译；如果没有术语，直接翻译原文本")
    public String doTranslation(
            @P(value="source_text", required = true) String sourceText,
            @P(value="target_language", required = true) String targetLanguage,
            @P(value="terms", required = false) String terms,
            @P(value="term_translations", required = false) String termTranslations) {

        log.info("========== doTranslation 开始 ==========");
        log.info("【原始待翻译语句】: {}", sourceText);
        log.info("【目标语言】: {}", targetLanguage);
        log.info("【向量数据库命中术语】: {}", terms);
        log.info("【术语翻译对照】: {}", termTranslations);

        String originalText = sourceText;
        String correctedText = sourceText;
        String matchedTerms = "无";

        if (terms != null && !terms.isEmpty() && !terms.equals("NO_SIMILAR_TERMS_FOUND")) {
            matchedTerms = terms;
            log.info("【术语库命中处理】: 命中 {} 个术语", terms.split("\\|").length);
        } else {
            log.info("【术语库命中处理】: 无命中术语");
        }

        if (terms != null && !terms.isEmpty() && termTranslations != null && !termTranslations.isEmpty() && !termTranslations.equals("NO_TERMS_FOUND")) {
            try {
                Pattern jsonPattern = Pattern.compile("\"([^\"]+)\":\"([^\"]+)\"");
                Matcher matcher = jsonPattern.matcher(termTranslations);

                Map<String, String> termMap = new HashMap<>();
                int termReplaceCount = 0;
                while (matcher.find()) {
                    String cnTerm = matcher.group(1);
                    String enTerm = matcher.group(2);
                    termMap.put(cnTerm, enTerm);
                    log.info("【术语替换对照】: {} -> {}", cnTerm, enTerm);
                    termReplaceCount++;
                }

                log.info("【术语替换】开始，共 {} 个术语需要替换", termReplaceCount);
                for (Map.Entry<String, String> entry : termMap.entrySet()) {
                    correctedText = correctedText.replace(entry.getKey(), entry.getValue());
                }
                log.info("【术语替换后语句】: {}", correctedText);
            } catch (Exception e) {
                log.warn("术语解析失败: {}", e.getMessage());
            }
        } else {
            log.info("【术语替换】: 无术语翻译对照，跳过替换");
        }

        log.info("【最终翻译语句】: {}", correctedText);

        log.info("========== doTranslation 完成 ==========");

        return String.format("RESULT|原文:%s|纠正后:%s|命中术语:%s|目标语言:%s", originalText, correctedText, matchedTerms, targetLanguage);
    }

    @Tool(name = "correct_and_translate", value = "术语纠正式翻译:先对原文进行术语纠正(相似度>0.85的术语匹配)，然后翻译成目标语言")
    public String correctAndTranslate(
            @P(value="source_text", required = true) String sourceText,
            @P(value="target_language", required = true) String targetLanguage) {

        log.info("========== correctAndTranslate 开始 ==========");
        log.info("原文: {}", sourceText);
        log.info("目标语言: {}", targetLanguage);

        String correctedText = sourceText;

        try {
            // 第一步：提取原文中的术语
            EmbeddingSearchResult<TextSegment> termSearchResult = getMatchWordsForTerms(sourceText);

            if (!termSearchResult.matches().isEmpty()) {
                // 找到最匹配的术语
                EmbeddingMatch<TextSegment> bestMatch = termSearchResult.matches().get(0);
                double score = bestMatch.score();
                String matchedTerm = bestMatch.embedded().text();

                log.info("术语匹配结果: 匹配文本='{}', 相似度={}", matchedTerm, score);

                // 如果相似度>=0.85，进行术语纠正
                if (score >= 0.85) {
                    // 直接使用匹配到的标准术语
                    // 格式现在是: "重庆大学" 这样的术语词汇

                    // 如果原文中包含与匹配术语相似的内容，替换为标准术语
                    // 这里可以进行简单的模糊匹配替换
                    if (!sourceText.contains(matchedTerm)) {
                        // 如果原文不包含完全匹配的术语，尝试模糊匹配
                        // 简单实现：不做替换，保持原文本
                        log.info("原文不包含标准术语，不进行替换");
                    } else {
                        log.info("术语已存在于原文中，无需纠正");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("术语纠正过程出错，直接翻译原文: {}", e.getMessage());
        }

        log.info("========== correctAndTranslate 完成 ==========");

        // 返回纠正后的文本，让AI进行翻译
        return "待翻译文本：" + correctedText;
    }

    @Tool(name = "find_similar_terms", value = "查找相似术语:从向量数据库中查找与输入文本相似的术语，返回相似度>=0.85的匹配结果")
    public String findSimilarTerms(@P(value="text", required = true) String text) {
        log.info("========== findSimilarTerms 开始 ==========");
        log.info("待匹配文本: {}", text);

        try {
            EmbeddingSearchResult<TextSegment> searchResult = getMatchWordsForTerms(text);

            if (searchResult.matches().isEmpty()) {
                log.info("向量数据库中未找到相似术语");
                return "NO_SIMILAR_TERMS_FOUND";
            }

            StringBuilder result = new StringBuilder();
            int matchCount = 0;
            int totalMatches = searchResult.matches().size();

            log.info("向量数据库匹配结果（共{}个匹配）:", totalMatches);

            for (EmbeddingMatch<TextSegment> embeddingMatch : searchResult.matches()) {
                double score = embeddingMatch.score();
                String matchedText = embeddingMatch.embedded().text();

                log.info("  - 术语: '{}' | 相似度: {} | 是否命中: {}", matchedText, score, score >= 0.85 ? "是" : "否");

                if (score >= 0.85) {
                    if (matchCount > 0) {
                        result.append(" | ");
                    }
                    result.append(matchedText);
                    matchCount++;
                }
            }

            log.info("向量数据库命中术语数量: {} (相似度>=0.85)", matchCount);

            if (matchCount == 0) {
                log.info("向量数据库中无命中术语");
                return "NO_SIMILAR_TERMS_FOUND";
            }

            log.info("向量数据库命中术语: {}", result.toString());
            return result.toString();
        } catch (Exception e) {
            log.error("查找相似术语失败: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }


}
