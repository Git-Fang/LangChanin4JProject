package org.fb.matcher;

import lombok.extern.slf4j.Slf4j;
import org.fb.model.TermMatchResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 优先级融合器
 * 负责融合多维度匹配结果并计算最终分数
 */
@Slf4j
@Component
public class PriorityFusion {

    /**
     * 融合多个匹配结果
     */
    public List<TermMatchResult> fuseResults(List<TermMatchResult> allResults, double threshold) {
        if (allResults == null || allResults.isEmpty()) {
            return Collections.emptyList();
        }

        // 按术语分组
        Map<String, List<TermMatchResult>> termGroups = allResults.stream()
                .collect(Collectors.groupingBy(TermMatchResult::getTerm));

        // 对每个术语组进行融合
        List<TermMatchResult> fusedResults = new ArrayList<>();
        for (Map.Entry<String, List<TermMatchResult>> entry : termGroups.entrySet()) {
            String term = entry.getKey();
            List<TermMatchResult> matches = entry.getValue();

            TermMatchResult fusedResult = fuseSingleTermMatches(term, matches);
            if (fusedResult != null) {
                fusedResult.checkThreshold(threshold);
                fusedResults.add(fusedResult);
            }
        }

        // 按最终分数排序
        fusedResults.sort((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()));

        log.info("融合完成: 原始 {} 个结果 -> 融合 {} 个结果", allResults.size(), fusedResults.size());
        return fusedResults;
    }

    /**
     * 融合单个术语的多个匹配结果
     */
    private TermMatchResult fuseSingleTermMatches(String term, List<TermMatchResult> matches) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }

        // 取各维度最高分数
        double maxSemantic = matches.stream().mapToDouble(TermMatchResult::getSemanticScore).max().orElse(0);
        double maxKeyword = matches.stream().mapToDouble(TermMatchResult::getKeywordScore).max().orElse(0);
        double maxPinyin = matches.stream().mapToDouble(TermMatchResult::getPinyinScore).max().orElse(0);
        double maxChar = matches.stream().mapToDouble(TermMatchResult::getCharScore).max().orElse(0);

        // 获取主要匹配类型
        String primaryType = determinePrimaryMatchType(maxSemantic, maxKeyword, maxPinyin, maxChar);

        // 获取优先级（取最高优先级）
        String priority = matches.stream()
                .map(TermMatchResult::getPriority)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(this::getPriorityWeight))
                .orElse("medium");

        // 构建融合结果
        TermMatchResult fused = TermMatchResult.builder()
                .term(term)
                .semanticScore(maxSemantic)
                .keywordScore(maxKeyword)
                .pinyinScore(maxPinyin)
                .charScore(maxChar)
                .matchType(primaryType)
                .priority(priority)
                .matchedText(matches.get(0).getMatchedText())
                .build();

        // 计算最终分数
        fused.calculateFinalScore();

        log.debug("术语 '{}' 融合结果: 语义={}, 关键词={}, 拼音={}, 字符={}, 最终={}, 类型={}",
                term, maxSemantic, maxKeyword, maxPinyin, maxChar, fused.getFinalScore(), primaryType);

        return fused;
    }

    /**
     * 确定主要匹配类型
     */
    private String determinePrimaryMatchType(double semantic, double keyword, double pinyin, double charScore) {
        double[] scores = {semantic, keyword, pinyin, charScore};
        String[] types = {"semantic", "keyword", "pinyin", "character"};

        int maxIndex = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[maxIndex]) {
                maxIndex = i;
            }
        }

        // 如果最大分数小于0.5，认为是混合匹配
        if (scores[maxIndex] < 0.5) {
            return "mixed";
        }

        return types[maxIndex];
    }

    /**
     * 获取优先级权重
     */
    private int getPriorityWeight(String priority) {
        if (priority == null) return 1;
        return switch (priority.toLowerCase()) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 2;
        };
    }

    /**
     * 去重合并相似术语
     */
    public List<TermMatchResult> deduplicateAndMerge(List<TermMatchResult> results, double similarityThreshold) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<TermMatchResult> deduplicated = new ArrayList<>();
        Set<String> processedTerms = new HashSet<>();

        for (TermMatchResult result : results) {
            String term = result.getTerm();
            if (processedTerms.contains(term)) {
                continue;
            }

            // 检查是否与已处理术语相似
            boolean isSimilar = false;
            for (String processedTerm : processedTerms) {
                double similarity = calculateSimilarity(term, processedTerm);
                if (similarity >= similarityThreshold) {
                    isSimilar = true;
                    break;
                }
            }

            if (!isSimilar) {
                deduplicated.add(result);
                processedTerms.add(term);
            }
        }

        return deduplicated;
    }

    /**
     * 计算术语相似度（简化版）
     */
    private double calculateSimilarity(String term1, String term2) {
        if (term1 == null || term2 == null) return 0;
        if (term1.equals(term2)) return 1.0;

        // 简单的字符重叠相似度
        Set<Character> set1 = new HashSet<>();
        Set<Character> set2 = new HashSet<>();

        for (char c : term1.toCharArray()) set1.add(c);
        for (char c : term2.toCharArray()) set2.add(c);

        Set<Character> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Character> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0;
        return (double) intersection.size() / union.size();
    }
}