package org.fb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 术语匹配结果模型
 * 用于记录多维度匹配的分数和详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermMatchResult {

    /**
     * 匹配的术语
     */
    private String term;

    /**
     * 语义匹配分数 (0.0 - 1.0)
     */
    @Builder.Default
    private double semanticScore = 0.0;

    /**
     * 关键词匹配分数 (0.0 - 1.0)
     */
    @Builder.Default
    private double keywordScore = 0.0;

    /**
     * 拼音匹配分数 (0.0 - 1.0)
     */
    @Builder.Default
    private double pinyinScore = 0.0;

    /**
     * 字符匹配分数 (0.0 - 1.0)
     */
    @Builder.Default
    private double charScore = 0.0;

    /**
     * 最终融合分数 (0.0 - 1.0)
     */
    @Builder.Default
    private double finalScore = 0.0;

    /**
     * 匹配类型
     * semantic, keyword, pinyin, character, mixed
     */
    private String matchType;

    /**
     * 原始匹配文本
     */
    private String matchedText;

    /**
     * 术语优先级
     */
    private String priority;

    /**
     * 是否超过阈值
     */
    @Builder.Default
    private boolean exceedsThreshold = false;

    /**
     * 计算最终融合分数
     * 权重：语义×0.4 + 关键词×0.3 + 拼音×0.2 + 字符×0.1
     */
    public void calculateFinalScore() {
        double weightedScore = 
            semanticScore * 0.4 + 
            keywordScore * 0.3 + 
            pinyinScore * 0.2 + 
            charScore * 0.1;

        // 应用优先级权重
        double priorityWeight = getPriorityWeight(priority);
        this.finalScore = weightedScore * priorityWeight;
    }

    /**
     * 获取优先级权重
     */
    private double getPriorityWeight(String priority) {
        if (priority == null) return 1.0;
        return switch (priority.toLowerCase()) {
            case "high" -> 1.2;
            case "medium" -> 1.0;
            case "low" -> 0.8;
            default -> 1.0;
        };
    }

    /**
     * 判断是否超过阈值
     */
    public void checkThreshold(double threshold) {
        this.exceedsThreshold = this.finalScore >= threshold;
    }

    /**
     * 获取主要匹配类型
     */
    public String getPrimaryMatchType() {
        if (semanticScore >= 0.7) return "semantic";
        if (keywordScore >= 0.8) return "keyword";
        if (pinyinScore >= 0.6) return "pinyin";
        if (charScore >= 0.6) return "character";
        return "mixed";
    }
}