package org.fb.bean;

import java.io.Serializable;

/**
 * 文档切分摘要结果
 */
public class SummaryChunk implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 文章标题
     */
    private String articleTitle;

    /**
     * 段落摘要（150字以内）
     */
    private String summary;

    /**
     * 切片内容（原始切分文本）
     */
    private String content;

    /**
     * 切片序号
     */
    private int chunkIndex;

    /**
     * 切片字符数
     */
    private int charCount;

    public SummaryChunk() {
    }

    public SummaryChunk(String articleTitle, String summary, String content, int chunkIndex) {
        this.articleTitle = articleTitle;
        this.summary = summary;
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.charCount = content != null ? content.length() : 0;
    }

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.charCount = content != null ? content.length() : 0;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    @Override
    public String toString() {
        return "SummaryChunk{" +
                "articleTitle='" + articleTitle + '\'' +
                ", summary='" + summary + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", charCount=" + charCount +
                '}';
    }
}
