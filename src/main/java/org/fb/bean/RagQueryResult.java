package org.fb.bean;

import java.io.Serializable;
import java.util.List;

/**
 * RAG查询结果
 */
public class RagQueryResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 生成的答案（如果启用了LLM生成）
     */
    private String answer;

    /**
     * 相关的文档切片列表
     */
    private List<RelevantChunk> relevantChunks;

    /**
     * 查询耗时（毫秒）
     */
    private long queryTimeMs;

    /**
     * 搜索到的相关片段数量
     */
    private int matchedCount;

    /**
     * 是否使用LLM生成答案
     */
    private boolean generatedWithLlm;

    public RagQueryResult() {
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<RelevantChunk> getRelevantChunks() {
        return relevantChunks;
    }

    public void setRelevantChunks(List<RelevantChunk> relevantChunks) {
        this.relevantChunks = relevantChunks;
    }

    public long getQueryTimeMs() {
        return queryTimeMs;
    }

    public void setQueryTimeMs(long queryTimeMs) {
        this.queryTimeMs = queryTimeMs;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public boolean isGeneratedWithLlm() {
        return generatedWithLlm;
    }

    public void setGeneratedWithLlm(boolean generatedWithLlm) {
        this.generatedWithLlm = generatedWithLlm;
    }

    /**
     * 相关文档切片
     */
    public static class RelevantChunk implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 文章标题
         */
        private String articleTitle;

        /**
         * 切片摘要
         */
        private String summary;

        /**
         * 切片内容
         */
        private String content;

        /**
         * 相似度分数
         */
        private double score;

        /**
         * 切片索引
         */
        private int chunkIndex;

        public RelevantChunk() {
        }

        public RelevantChunk(String articleTitle, String summary, String content, double score, int chunkIndex) {
            this.articleTitle = articleTitle;
            this.summary = summary;
            this.content = content;
            this.score = score;
            this.chunkIndex = chunkIndex;
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
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }

    @Override
    public String toString() {
        return "RagQueryResult{" +
                "matchedCount=" + matchedCount +
                ", queryTimeMs=" + queryTimeMs +
                ", generatedWithLlm=" + generatedWithLlm +
                '}';
    }
}
