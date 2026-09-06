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
import org.fb.matcher.MultiDimensionalTermMatcher;
import org.fb.model.TermMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    private MultiDimensionalTermMatcher multiDimensionalTermMatcher;


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

        // 找到相似度最高的术语
        double bestScore = 0;
        String bestTerm = null;
        int totalMatches = searchResult.matches().size();

        log.info("【术语翻译查询】: 向量数据库共返回 {} 个匹配", totalMatches);

        for (int i = 0; i < searchResult.matches().size(); i++) {
            EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(i);
            double score = embeddingMatch.score();
            String matchedText = embeddingMatch.embedded().text();
            
            // 解析JSON格式，提取实际术语
            String actualTerm = parseTermFromJson(matchedText);
            log.info("  - 原始存储: '{}' | 提取术语: '{}' | 相似度: {} | 类型: TERMS", matchedText, actualTerm, score);
            
            // 只处理相似度>=0.8的匹配，保留相似度最高的术语
            if (score >= 0.8 && actualTerm != null && !actualTerm.isEmpty()) {
                if (score > bestScore) {
                    bestScore = score;
                    bestTerm = actualTerm;
                }
            }
        }

        log.info("【术语翻译查询结果】: 最高相似度: {}, 命中术语: {}", bestScore, bestTerm);
        log.info("========== embedding_search_for_terms 完成 ==========");

        if (bestTerm == null) {
            return "NO_TERMS_FOUND";
        }
        
        // 返回相似度最高的术语
        return bestTerm;
    }



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
                    .minScore(0.8)
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

        // 如果terms参数为空，尝试直接从向量数据库查询相似术语
        if (terms == null || terms.isEmpty() || terms.equals("NO_SIMILAR_TERMS_FOUND")) {
            log.info("【术语参数为空，尝试直接从向量数据库查询】");
            try {
                String directTerms = findSimilarTerms(sourceText);
                if (directTerms != null && !directTerms.equals("NO_SIMILAR_TERMS_FOUND")) {
                    terms = directTerms;
                    log.info("【直接查询到术语】: {}", terms);
                }
            } catch (Exception e) {
                log.warn("直接查询术语失败: {}", e.getMessage());
            }
        }

        // 处理命中的相似术语
        if (terms != null && !terms.isEmpty() && !terms.equals("NO_SIMILAR_TERMS_FOUND")) {
            matchedTerms = terms;
            log.info("【术语库命中处理】: 命中 {} 个术语", terms.split("\\|").length);
            
            // 用正确的标准术语替换原文中的相似词
            correctedText = correctTermInText(sourceText, terms);
            
            log.info("【术语纠正后语句】: {}", correctedText);
        } else {
            log.info("【术语库命中处理】: 无命中术语");
        }

        log.info("【最终翻译语句】: {}", correctedText);

        log.info("========== doTranslation 完成 ==========");

        return String.format("RESULT|原文:%s|纠正后:%s|命中术语:%s|目标语言:%s", originalText, correctedText, matchedTerms, targetLanguage);
    }

    /**
     * 纠正原文中的错误术语
     * 使用相似度匹配找到原文中的错误词，并用正确的标准术语替换
     * 
     * 例如：原文 "火中取碳是一个成语" + 标准术语 "火中取栗"
     *      结果："火中取栗是一个成语"
     */
    private String correctTermInText(String text, String standardTerms) {
        if (text == null || standardTerms == null || text.isEmpty() || standardTerms.isEmpty()) {
            return text;
        }

        // 支持多个标准术语（用 | 分隔）
        String[] terms = standardTerms.split("\\|");
        
        for (String standardTerm : terms) {
            standardTerm = standardTerm.trim();
            if (standardTerm.isEmpty()) continue;
            
            // 查找原文中最相似的子串并替换
            String replacement = findAndReplaceSimilarTerm(text, standardTerm);
            if (!replacement.equals(text)) {
                log.info("【术语替换成功】: 原文本 '{}' -> '{}'", text, replacement);
                return replacement;
            }
        }
        
        return text;
    }

    /**
     * 找到原文中最相似的子串并替换为标准术语
     * 使用滑动窗口 + 字符重叠检测
     */
    private String findAndReplaceSimilarTerm(String text, String standardTerm) {
        int termLen = standardTerm.length();
        int textLen = text.length();
        
        if (termLen > textLen) {
            // 标准术语比原文还长，尝试在原文首尾添加前后文进行匹配
            // 例如：原文 "火中取碳" -> 尝试匹配 "火中取碳"
            // 标准术语 "火中取栗" -> 比较两者的相似度
            return findAndReplaceBySimilarity(text, standardTerm);
        }
        
        // 滑动窗口检查
        // 从长度差异最小的开始尝试匹配
        for (int windowSize = termLen; windowSize >= 2; windowSize--) {
            for (int i = 0; i <= textLen - windowSize; i++) {
                String substring = text.substring(i, i + windowSize);
                
                // 如果子串与标准术语有共同字符（至少50%的字符重叠）
                // 且不完全相同（说明是错误的写法）
                if (!substring.equals(standardTerm) && isSimilar(substring, standardTerm, 0.4)) {
                    // 替换这个子串为标准术语
                    String result = text.substring(0, i) + standardTerm + text.substring(i + windowSize);
                    log.info("【相似术语替换】 '{}' -> '{}'", substring, standardTerm);
                    return result;
                }
            }
        }
        
        // 如果窗口匹配没找到，尝试整体相似度匹配
        return findAndReplaceBySimilarity(text, standardTerm);
    }

    /**
     * 通过整体相似度匹配进行替换
     */
    private String findAndReplaceBySimilarity(String text, String standardTerm) {
        // 提取所有可能的词（2-6个字符）
        // 例如：从 "火中取碳是一个成语" 中提取 "火中取碳"
        List<String> candidates = new ArrayList<>();
        
        // 提取连续的中文字符序列
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");
        Matcher matcher = chinesePattern.matcher(text);
        while (matcher.find()) {
            candidates.add(matcher.group());
        }
        
        // 找到最相似的候选词并替换
        double bestSimilarity = 0;
        String bestCandidate = null;
        int bestIndex = -1;
        
        for (String candidate : candidates) {
            double similarity = calculateSimilarity(candidate, standardTerm);
            if (similarity > bestSimilarity && similarity < 1.0) { // < 1.0 表示不是完全匹配
                bestSimilarity = similarity;
                bestCandidate = candidate;
                bestIndex = text.indexOf(candidate);
            }
        }
        
        // 如果找到相似度足够高的候选词（>50%）且不是完全匹配
        if (bestCandidate != null && bestSimilarity > 0.5 && bestIndex >= 0) {
            String result = text.substring(0, bestIndex) + standardTerm + text.substring(bestIndex + bestCandidate.length());
            log.info("【相似度替换】 '{}' (相似度{}) -> '{}'", bestCandidate, String.format("%.2f", bestSimilarity), standardTerm);
            return result;
        }
        
        return text;
    }

    /**
     * 计算两个字符串的相似度（简单实现：字符重叠率）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return 0;
        }
        
        // 计算字符重叠数
        Set<Character> set1 = new HashSet<>();
        Set<Character> set2 = new HashSet<>();
        
        for (char c : s1.toCharArray()) set1.add(c);
        for (char c : s2.toCharArray()) set2.add(c);
        
        // 计算 Jaccard 相似度
        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) return 0;
        
        return (double) intersection.size() / union.size();
    }

    /**
     * 检查两个字符串是否相似（基于字符重叠）
     */
    private boolean isSimilar(String s1, String s2, double threshold) {
        return calculateSimilarity(s1, s2) >= threshold;
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

    @Tool(name = "find_similar_terms", value = "查找相似术语:从向量数据库中查找与输入文本相似的术语，返回相似度>=0.8的匹配结果")
    public String findSimilarTerms(@P(value="text", required = true) String text) {
        log.info("========== findSimilarTerms 开始 ==========");
        log.info("待匹配文本: {}", text);

        try {
            EmbeddingSearchResult<TextSegment> searchResult = getMatchWordsForTerms(text);

            if (searchResult.matches().isEmpty()) {
                log.info("向量数据库中未找到相似术语");
                return "NO_SIMILAR_TERMS_FOUND";
            }

            // 找到相似度最高的术语
            double bestScore = 0;
            String bestTerm = null;
            int totalMatches = searchResult.matches().size();

            log.info("向量数据库匹配结果（共{}个匹配）:", totalMatches);

            for (EmbeddingMatch<TextSegment> embeddingMatch : searchResult.matches()) {
                double score = embeddingMatch.score();
                String matchedText = embeddingMatch.embedded().text();
                
                // 解析JSON格式的存储内容，提取实际术语
                String actualTerm = parseTermFromJson(matchedText);
                log.info("  - 原始存储: '{}' | 提取术语: '{}' | 相似度: {} | 是否命中: {}", 
                    matchedText, actualTerm, score, score >= 0.8 ? "是" : "否");

                // 阈值0.8，保留相似度最高的术语
                if (score >= 0.8 && actualTerm != null && !actualTerm.isEmpty()) {
                    if (score > bestScore) {
                        bestScore = score;
                        bestTerm = actualTerm;
                    }
                }
            }

            log.info("向量数据库最高相似度: {}, 命中术语: {}", bestScore, bestTerm);

            if (bestTerm == null || bestScore < 0.8) {
                log.info("向量数据库中无符合阈值的术语");
                return "NO_SIMILAR_TERMS_FOUND";
            }

            log.info("【最终返回术语】: {} (相似度: {})", bestTerm, bestScore);
            return bestTerm;
        } catch (Exception e) {
            log.error("查找相似术语失败: {}", e.getMessage(), e);
                        return "ERROR: " + e.getMessage();
                    }
                }

                    /**
                         * 多维度术语匹配工具
                         * 整合语义、关键词、拼音、字符四种匹配方式
                         */
                        @Tool(name = "multi_dimensional_term_match", value = "多维度术语匹配:使用语义、关键词、拼音、字符四种方式匹配术语，返回最相关的术语")
                        public String multiDimensionalTermMatch(@P(value = "text", required = true) String text) {
                            log.info("========== 多维度术语匹配工具开始 ==========");
                            log.info("输入文本: {}", text);

                            try {
                                if (multiDimensionalTermMatcher == null) {
                                    log.warn("多维度匹配器未初始化，使用传统方式");
                                    return findSimilarTerms(text);
                                }

                                // 执行多维度匹配
                                List<TermMatchResult> results = multiDimensionalTermMatcher.findMatchingTerms(text);

                                if (results.isEmpty()) {
                                    log.info("未找到匹配术语");
                                    return "NO_TERMS_FOUND";
                                }

                                // 返回最佳匹配结果
                                TermMatchResult bestResult = results.get(0);
                                log.info("最佳匹配: 术语={}, 最终分数={}, 匹配类型={}", 
                                    bestResult.getTerm(), bestResult.getFinalScore(), bestResult.getMatchType());

                                log.info("========== 多维度术语匹配工具完成 ==========");
                                return bestResult.getTerm();

                            } catch (Exception e) {
                                log.error("多维度术语匹配失败: {}", e.getMessage(), e);
                                return "ERROR: " + e.getMessage();
                            }
                        }

                        /**
                         * 从JSON格式的存储内容中提取实际术语
     * 例如: {"terms":"火中取栗","term_count":1} -> "火中取栗"
     */
    private String parseTermFromJson(String storedText) {
        if (storedText == null || storedText.isEmpty()) {
            return storedText;
        }
        
        // 尝试解析JSON格式
        if (storedText.startsWith("{")) {
            try {
                // 提取 "terms":"xxx" 部分
                Pattern termPattern = Pattern.compile("\"terms\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = termPattern.matcher(storedText);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                log.warn("JSON解析失败: {}", e.getMessage());
            }
        }
        
        // 如果不是JSON格式，直接返回原文本
        return storedText;
    }


}
