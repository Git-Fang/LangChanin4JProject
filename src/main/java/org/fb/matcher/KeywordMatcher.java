package org.fb.matcher;

import lombok.extern.slf4j.Slf4j;
import org.fb.model.TermMatchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词匹配器
 * 支持完全匹配、部分匹配和同义词匹配
 */
@Slf4j
@Component
public class KeywordMatcher {

    /**
     * 完全匹配
     */
    public TermMatchResult exactMatch(String text, String term, String priority) {
        if (text.contains(term)) {
            log.debug("完全匹配: 文本 '{}' 包含术语 '{}'", text, term);
            return TermMatchResult.builder()
                .term(term)
                .keywordScore(1.0)
                .matchType("keyword_exact")
                .matchedText(term)
                .priority(priority)
                .exceedsThreshold(true)
                .build();
        }
        return null;
    }

    /**
     * 部分匹配
     * 检查文本中是否包含术语的一部分
     */
    public TermMatchResult partialMatch(String text, String term, String priority) {
        if (term == null || term.length() < 2) return null;

        // 尝试匹配术语的子串
        for (int len = term.length(); len >= 2; len--) {
            for (int i = 0; i <= term.length() - len; i++) {
                String substring = term.substring(i, i + len);
                if (text.contains(substring)) {
                    double score = (double) len / term.length();
                    if (score >= 0.6) {  // 至少60%的字符匹配
                        log.debug("部分匹配: 文本 '{}' 包含术语 '{}' 的部分 '{}' (分数: {})", 
                            text, term, substring, score);
                        return TermMatchResult.builder()
                            .term(term)
                            .keywordScore(score)
                            .matchType("keyword_partial")
                            .matchedText(substring)
                            .priority(priority)
                            .exceedsThreshold(true)
                            .build();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 同义词匹配
     */
    public TermMatchResult synonymMatch(String text, List<String> aliases, String standardTerm, String priority) {
        if (aliases == null || aliases.isEmpty()) return null;

        for (String alias : aliases) {
            if (text.contains(alias)) {
                log.debug("同义词匹配: 文本 '{}' 包含术语别名 '{}' (标准术语: '{}')", 
                    text, alias, standardTerm);
                return TermMatchResult.builder()
                    .term(standardTerm)
                    .keywordScore(0.9)  // 同义词匹配分数略低于完全匹配
                    .matchType("keyword_synonym")
                    .matchedText(alias)
                    .priority(priority)
                    .exceedsThreshold(true)
                    .build();
            }
        }
        return null;
    }

    /**
     * 关键词匹配主方法
     * 依次尝试完全匹配、同义词匹配、部分匹配
     */
    public List<TermMatchResult> match(String text, String term, List<String> aliases, String priority) {
        List<TermMatchResult> results = new ArrayList<>();

        // 1. 尝试完全匹配
        TermMatchResult exactMatchResult = exactMatch(text, term, priority);
        if (exactMatchResult != null) {
            results.add(exactMatchResult);
            exactMatchResult.calculateFinalScore();
            return results;  // 完全匹配优先，直接返回
        }

        // 2. 尝试同义词匹配
        if (aliases != null && !aliases.isEmpty()) {
            TermMatchResult synonymMatchResult = synonymMatch(text, aliases, term, priority);
            if (synonymMatchResult != null) {
                results.add(synonymMatchResult);
                synonymMatchResult.calculateFinalScore();
                return results;  // 同义词匹配优先
            }
        }

        // 3. 尝试部分匹配
        TermMatchResult partialMatchResult = partialMatch(text, term, priority);
        if (partialMatchResult != null) {
            results.add(partialMatchResult);
            partialMatchResult.calculateFinalScore();
        }

        return results;
    }
}