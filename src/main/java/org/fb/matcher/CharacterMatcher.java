package org.fb.matcher;

import lombok.extern.slf4j.Slf4j;
import org.fb.model.TermMatchResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 字符匹配器
 * 基于字符重叠和相似度的匹配算法
 */
@Slf4j
@Component
public class CharacterMatcher {

    /**
     * 计算Jaccard字符重叠相似度
     */
    public double calculateJaccardSimilarity(String s1, String s2) {
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
     * 字符匹配主方法
     */
    public TermMatchResult match(String text, String term, double threshold, String priority) {
        if (term == null || term.isEmpty() || text == null || text.isEmpty()) {
            return null;
        }

        // 尝试在文本中查找相似度最高的子串
        TermMatchResult bestMatch = findBestCharacterMatch(text, term, threshold, priority);
        if (bestMatch != null) {
            bestMatch.calculateFinalScore();
        }
        return bestMatch;
    }

    /**
     * 在文本中查找最佳字符匹配
     */
    private TermMatchResult findBestCharacterMatch(String text, String term, double threshold, String priority) {
        int termLen = term.length();
        int textLen = text.length();
        double bestScore = 0;
        String bestMatch = null;

        if (termLen > textLen) {
            // 标准术语比原文长，整体比较相似度
            double similarity = calculateJaccardSimilarity(text, term);
            if (similarity >= threshold) {
                log.debug("字符匹配（整体）: 文本 '{}' vs 术语 '{}' 相似度: {}", text, term, similarity);
                return TermMatchResult.builder()
                    .term(term)
                    .charScore(similarity)
                    .matchType("character_jaccard")
                    .matchedText(text)
                    .priority(priority)
                    .exceedsThreshold(true)
                    .build();
            }
            return null;
        }

        // 滑动窗口检查
        for (int windowSize = termLen; windowSize >= 2; windowSize--) {
            for (int i = 0; i <= textLen - windowSize; i++) {
                String substring = text.substring(i, i + windowSize);
                double similarity = calculateJaccardSimilarity(substring, term);

                if (similarity > bestScore && similarity >= threshold) {
                    bestScore = similarity;
                    bestMatch = substring;
                }
            }
        }

        if (bestMatch != null) {
            log.debug("字符匹配（窗口）: 文本 '{}' 中找到 '{}' vs 术语 '{}' 相似度: {}", 
                text, bestMatch, term, bestScore);
            return TermMatchResult.builder()
                .term(term)
                .charScore(bestScore)
                .matchType("character_window")
                .matchedText(bestMatch)
                .priority(priority)
                .exceedsThreshold(true)
                .build();
        }

        return null;
    }

    /**
     * 检查两个字符串是否相似（基于字符重叠）
     */
    public boolean isSimilar(String s1, String s2, double threshold) {
        return calculateJaccardSimilarity(s1, s2) >= threshold;
    }
}