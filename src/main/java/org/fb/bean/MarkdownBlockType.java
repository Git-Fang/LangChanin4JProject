package org.fb.bean;

/**
 * Markdown内容块类型枚举
 */
public enum MarkdownBlockType {
    /**
     * 普通段落文本
     */
    PARAGRAPH,
    
    /**
     * 标题 (h1-h6)
     */
    HEADING,
    
    /**
     * 代码块 (包含Mermaid、PlantUML等图表)
     */
    CODE_BLOCK,
    
    /**
     * 行内代码
     */
    INLINE_CODE,
    
    /**
     * 表格
     */
    TABLE,
    
    /**
     * 图片
     */
    IMAGE,
    
    /**
     * 有序列表
     */
    ORDERED_LIST,
    
    /**
     * 无序列表
     */
    UNORDERED_LIST,
    
    /**
     * 引用块
     */
    BLOCKQUOTE,
    
    /**
     * 分隔线
     */
    HORIZONTAL_RULE,
    
    /**
     * HTML块
     */
    HTML_BLOCK,
    
    /**
     * 数学公式块 (如LaTeX)
     */
    MATH_BLOCK,
    
    /**
     * 空行/未知类型
     */
    EMPTY
}
