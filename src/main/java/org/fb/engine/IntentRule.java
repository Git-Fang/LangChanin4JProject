package org.fb.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 意图识别规则定义类
 * 支持多模式匹配和优先级
 */
@Slf4j
@Getter
public class IntentRule {

    /**
     * 规则ID
     */
    private final String intent;

    /**
     * 匹配模式列表（OR 逻辑，任一匹配即匹配）
     */
    private final List<Pattern> patterns;

    /**
     * 规则优先级（数值越小优先级越高）
     */
    private final int priority;

    /**
     * 规则描述（用于日志）
     */
    private final String description;

    /**
     * 创建规则（默认优先级为 100）
     */
    public static IntentRule of(String intent, String... regexPatterns) {
        return of(intent, 100, List.of(regexPatterns), "");
    }

    /**
     * 创建规则（带描述）
     */
    public static IntentRule of(String intent, String description, String... regexPatterns) {
        return of(intent, 100, List.of(regexPatterns), description);
    }

    /**
     * 创建规则（带优先级）
     */
    public static IntentRule of(String intent, int priority, String... regexPatterns) {
        return of(intent, priority, List.of(regexPatterns), "");
    }

    /**
     * 创建规则（完整参数）
     */
    public static IntentRule of(String intent, int priority, List<String> regexPatterns, String description) {
        List<Pattern> patterns = regexPatterns.stream()
                .map(pattern -> {
                    try {
                        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    } catch (PatternSyntaxException e) {
                        log.error("无效的正则表达式: {}, 错误: {}", pattern, e.getMessage());
                        return null;
                    }
                })
                .filter(p -> p != null)
                .toList();

        return new IntentRule(intent, priority, patterns, description);
    }

    private IntentRule(String intent, int priority, List<Pattern> patterns, String description) {
        this.intent = intent;
        this.priority = priority;
        this.patterns = patterns;
        this.description = description;
    }

    /**
     * 检查消息是否匹配此规则
     */
    public boolean matches(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        // 遍历所有模式，任一匹配即返回 true
        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).find()) {
                log.debug("消息匹配规则: {} -> {}, 匹配模式: {}", intent, description, pattern.pattern());
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("IntentRule(intent=%s, priority=%d, patterns=%d, description='%s')",
                intent, priority, patterns.size(), description);
    }
}
