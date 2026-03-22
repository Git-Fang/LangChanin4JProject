package org.fb.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Markdown内容块数据结构
 * 用于表示Markdown文档中的各种内容块
 */
public class MarkdownBlock implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 块类型
     */
    private MarkdownBlockType type;
    
    /**
     * 原始内容
     */
    private String rawContent;
    
    /**
     * 处理后内容 (如图片被替换为描述文本)
     */
    private String processedContent;
    
    /**
     * 子块列表 (用于表格单元格、列表项等)
     */
    private List<MarkdownBlock> children;
    
    /**
     * 元数据 (如imageUrl, alt, language等)
     */
    private Map<String, Object> metadata;
    
    /**
     * 起始行号 (从0开始)
     */
    private int lineStart;
    
    /**
     * 结束行号
     */
    private int lineEnd;
    
    /**
     * 标题级别 (如果是HEADING类型)
     */
    private int headingLevel;
    
    /**
     * 缩进级别
     */
    private int indentLevel;
    
    public MarkdownBlock() {
        this.children = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    public MarkdownBlock(MarkdownBlockType type, String rawContent) {
        this();
        this.type = type;
        this.rawContent = rawContent;
        this.processedContent = rawContent;
    }
    
    public MarkdownBlock(MarkdownBlockType type, String rawContent, int lineStart, int lineEnd) {
        this(type, rawContent);
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
    }
    
    /**
     * 创建段落块
     */
    public static MarkdownBlock paragraph(String content, int lineStart, int lineEnd) {
        return new MarkdownBlock(MarkdownBlockType.PARAGRAPH, content, lineStart, lineEnd);
    }
    
    /**
     * 创建标题块
     */
    public static MarkdownBlock heading(String content, int level, int lineStart, int lineEnd) {
        MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.HEADING, content, lineStart, lineEnd);
        block.setHeadingLevel(level);
        return block;
    }
    
    /**
     * 创建代码块
     */
    public static MarkdownBlock codeBlock(String content, String language, int lineStart, int lineEnd) {
        MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.CODE_BLOCK, content, lineStart, lineEnd);
        block.getMetadata().put("language", language);
        return block;
    }
    
    /**
     * 创建图片块
     */
    public static MarkdownBlock image(String alt, String url, int lineStart, int lineEnd) {
        MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.IMAGE, "![alt](url)", lineStart, lineEnd);
        block.getMetadata().put("alt", alt);
        block.getMetadata().put("url", url);
        return block;
    }
    
    /**
     * 创建表格块
     */
    public static MarkdownBlock table(String rawTableContent, int lineStart, int lineEnd) {
        return new MarkdownBlock(MarkdownBlockType.TABLE, rawTableContent, lineStart, lineEnd);
    }
    
    // Getters and Setters
    
    public MarkdownBlockType getType() {
        return type;
    }
    
    public void setType(MarkdownBlockType type) {
        this.type = type;
    }
    
    public String getRawContent() {
        return rawContent;
    }
    
    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }
    
    public String getProcessedContent() {
        return processedContent != null ? processedContent : rawContent;
    }
    
    public void setProcessedContent(String processedContent) {
        this.processedContent = processedContent;
    }
    
    public List<MarkdownBlock> getChildren() {
        return children;
    }
    
    public void setChildren(List<MarkdownBlock> children) {
        this.children = children;
    }
    
    public void addChild(MarkdownBlock child) {
        this.children.add(child);
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void putMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    public int getLineStart() {
        return lineStart;
    }
    
    public void setLineStart(int lineStart) {
        this.lineStart = lineStart;
    }
    
    public int getLineEnd() {
        return lineEnd;
    }
    
    public void setLineEnd(int lineEnd) {
        this.lineEnd = lineEnd;
    }
    
    public int getHeadingLevel() {
        return headingLevel;
    }
    
    public void setHeadingLevel(int headingLevel) {
        this.headingLevel = headingLevel;
    }
    
    public int getIndentLevel() {
        return indentLevel;
    }
    
    public void setIndentLevel(int indentLevel) {
        this.indentLevel = indentLevel;
    }
    
    /**
     * 获取内容长度
     */
    public int getContentLength() {
        String content = getProcessedContent();
        return content != null ? content.length() : 0;
    }
    
    /**
     * 判断是否为大型块 (代码块、表格等)
     */
    public boolean isLargeBlock() {
        return type == MarkdownBlockType.CODE_BLOCK 
            || type == MarkdownBlockType.TABLE 
            || type == MarkdownBlockType.MATH_BLOCK;
    }
    
    /**
     * 判断是否为图表代码块
     */
    public boolean isDiagramBlock() {
        if (type != MarkdownBlockType.CODE_BLOCK) {
            return false;
        }
        String language = (String) metadata.get("language");
        if (language == null) {
            return false;
        }
        String lowerLang = language.toLowerCase();
        return lowerLang.equals("mermaid") 
            || lowerLang.equals("plantuml")
            || lowerLang.equals("uml");
    }
    
    /**
     * 判断是否为Mermaid图表
     */
    public boolean isMermaidBlock() {
        if (type != MarkdownBlockType.CODE_BLOCK) {
            return false;
        }
        String language = (String) metadata.get("language");
        return language != null && language.toLowerCase().equals("mermaid");
    }
    
    /**
     * 判断是否为PlantUML图表
     */
    public boolean isPlantUmlBlock() {
        if (type != MarkdownBlockType.CODE_BLOCK) {
            return false;
        }
        String language = (String) metadata.get("language");
        return language != null && language.toLowerCase().equals("plantuml");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarkdownBlock that = (MarkdownBlock) o;
        return lineStart == that.lineStart && 
               lineEnd == that.lineEnd && 
               type == that.type;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, lineStart, lineEnd);
    }
    
    @Override
    public String toString() {
        return "MarkdownBlock{" +
                "type=" + type +
                ", lineStart=" + lineStart +
                ", lineEnd=" + lineEnd +
                ", rawContent='" + (rawContent != null && rawContent.length() > 50 
                    ? rawContent.substring(0, 50) + "..." 
                    : rawContent) + '\'' +
                '}';
    }
}
