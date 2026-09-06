package org.fb.matcher;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.fb.model.TermMatchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼音匹配器
 * 支持全拼匹配、首字母匹配和模糊拼音匹配
 */
@Slf4j
@Component
public class PinyinMatcher {

    private final HanyuPinyinOutputFormat format;

    public PinyinMatcher() {
        format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /**
     * 获取字符串的拼音（全拼）
     */
    public String getFullPinyin(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder pinyin = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    pinyin.append(pinyinArray[0]);
                } else {
                    // 非中文字符保持原样
                    pinyin.append(c);
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                log.warn("拼音转换失败: {}", e.getMessage());
                pinyin.append(c);
            }
        }
        return pinyin.toString();
    }

    /**
     * 获取字符串的拼音首字母
     */
    public String getInitials(String text) {
        String fullPinyin = getFullPinyin(text);
        StringBuilder initials = new StringBuilder();
        
        for (int i = 0; i < fullPinyin.length(); i++) {
            char c = fullPinyin.charAt(i);
            // 如果是字母且是单词的首字母（或前一字符不是字母）
            if (Character.isLetter(c)) {
                if (i == 0 || !Character.isLetter(fullPinyin.charAt(i - 1))) {
                    initials.append(c);
                }
            }
        }
        return initials.toString();
    }

    /**
     * 全拼匹配
     */
    public TermMatchResult fullPinyinMatch(String text, String term, String priority) {
        String termPinyin = getFullPinyin(term);
        if (text.toLowerCase().contains(termPinyin.toLowerCase())) {
            log.debug("全拼匹配: 文本 '{}' 包含术语 '{}' 的拼音 '{}'", text, term, termPinyin);
            return TermMatchResult.builder()
                .term(term)
                .pinyinScore(0.8)
                .matchType("pinyin_full")
                .matchedText(termPinyin)
                .priority(priority)
                .exceedsThreshold(true)
                .build();
        }
        return null;
    }

    /**
     * 首字母匹配
     */
    public TermMatchResult initialsMatch(String text, String term, String priority) {
        String termInitials = getInitials(term);
        String textInitials = getInitials(text);
        
        if (textInitials.toLowerCase().contains(termInitials.toLowerCase())) {
            log.debug("首字母匹配: 文本 '{}' 的首字母 '{}' 包含术语 '{}' 的首字母 '{}'", 
                text, textInitials, term, termInitials);
            return TermMatchResult.builder()
                .term(term)
                .pinyinScore(0.7)
                .matchType("pinyin_initials")
                .matchedText(termInitials)
                .priority(priority)
                .exceedsThreshold(true)
                .build();
        }
        return null;
    }

    /**
     * 模糊拼音匹配
     * 支持部分拼音匹配和声调忽略
     */
    public TermMatchResult fuzzyPinyinMatch(String text, String term, String priority) {
        String termPinyin = getFullPinyin(term).toLowerCase();
        String textPinyin = getFullPinyin(text).toLowerCase();
        
        // 检查是否包含部分拼音
        for (int len = termPinyin.length(); len >= 3; len--) {
            for (int i = 0; i <= termPinyin.length() - len; i++) {
                String substring = termPinyin.substring(i, i + len);
                if (textPinyin.contains(substring)) {
                    double score = (double) len / termPinyin.length();
                    if (score >= 0.5) {  // 至少50%的拼音匹配
                        log.debug("模糊拼音匹配: 文本 '{}' 的拼音 '{}' 包含术语 '{}' 的拼音部分 '{}' (分数: {})", 
                            text, textPinyin, term, substring, score);
                        return TermMatchResult.builder()
                            .term(term)
                            .pinyinScore(score)
                            .matchType("pinyin_fuzzy")
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
     * 拼音匹配主方法
     * 依次尝试全拼匹配、首字母匹配、模糊拼音匹配
     */
    public List<TermMatchResult> match(String text, String term, String priority) {
        List<TermMatchResult> results = new ArrayList<>();

        // 1. 尝试全拼匹配
        TermMatchResult fullMatchResult = fullPinyinMatch(text, term, priority);
        if (fullMatchResult != null) {
            results.add(fullMatchResult);
            fullMatchResult.calculateFinalScore();
            return results;  // 全拼匹配优先
        }

        // 2. 尝试首字母匹配
        TermMatchResult initialsMatchResult = initialsMatch(text, term, priority);
        if (initialsMatchResult != null) {
            results.add(initialsMatchResult);
            initialsMatchResult.calculateFinalScore();
            return results;  // 首字母匹配优先
        }

        // 3. 尝试模糊拼音匹配
        TermMatchResult fuzzyMatchResult = fuzzyPinyinMatch(text, term, priority);
        if (fuzzyMatchResult != null) {
            results.add(fuzzyMatchResult);
            fuzzyMatchResult.calculateFinalScore();
        }

        return results;
    }
}