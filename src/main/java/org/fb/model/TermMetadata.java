package org.fb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 术语元数据模型 - 扩展术语存储结构
 * 支持多维度匹配所需的所有信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermMetadata {

    /**
     * 标准术语名称
     */
    private String term;

    /**
     * 术语别名列表（同义词、别称等）
     */
    private List<String> aliases;

    /**
     * 拼音列表（全拼、首字母等）
     */
    private List<String> pinyin;

    /**
     * 术语分类
     * political_figure, organization, location, etc.
     */
    private String category;

    /**
     * 术语优先级
     * high, medium, low
     */
    private String priority;

    /**
     * 多语言翻译映射
     */
    private Map<String, String> translations;

    /**
     * 关键词列表
     */
    private List<String> keywords;

    /**
     * 创建时间戳
     */
    private long timestamp;

    /**
     * JSON序列化方法
     */
    public String toJson() {
        return String.format(
            "{\"term\":\"%s\",\"aliases\":%s,\"pinyin\":%s,\"category\":\"%s\",\"priority\":\"%s\",\"translations\":%s,\"keywords\":%s,\"timestamp\":%d}",
            term,
            aliases != null ? aliases.toString() : "[]",
            pinyin != null ? pinyin.toString() : "[]",
            category,
            priority,
            translations != null ? translations.toString() : "{}",
            keywords != null ? keywords.toString() : "[]",
            timestamp
        );
    }

    /**
     * 从JSON解析
     */
    public static TermMetadata fromJson(String json) {
        // 简化实现，实际应使用Jackson等JSON库
        TermMetadata metadata = new TermMetadata();
        metadata.setTimestamp(System.currentTimeMillis());
        return metadata;
    }
}