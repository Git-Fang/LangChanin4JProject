package org.fb.bean;

import java.io.Serializable;
import java.util.List;

/**
 * 文档摘要结果
 * 封装切分摘要结果列表和向量入库状态
 */
public class SummarizationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 切分摘要结果列表
     */
    private List<SummaryChunk> chunks;

    /**
     * 向量入库结果
     */
    private VectorStoreResult vectorStoreResult;

    /**
     * 处理时间（毫秒）
     */
    private long processingTimeMs;

    public SummarizationResult() {
    }

    public SummarizationResult(List<SummaryChunk> chunks, VectorStoreResult vectorStoreResult, long processingTimeMs) {
        this.chunks = chunks;
        this.vectorStoreResult = vectorStoreResult;
        this.processingTimeMs = processingTimeMs;
    }

    /**
     * 创建仅包含摘要结果的成功响应（向量入库被跳过时）
     */
    public static SummarizationResult withChunksOnly(List<SummaryChunk> chunks, long processingTimeMs) {
        return new SummarizationResult(chunks, null, processingTimeMs);
    }

    /**
     * 创建包含完整结果的成功响应
     */
    public static SummarizationResult success(List<SummaryChunk> chunks, VectorStoreResult vectorStoreResult, long processingTimeMs) {
        return new SummarizationResult(chunks, vectorStoreResult, processingTimeMs);
    }

    public List<SummaryChunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<SummaryChunk> chunks) {
        this.chunks = chunks;
    }

    public VectorStoreResult getVectorStoreResult() {
        return vectorStoreResult;
    }

    public void setVectorStoreResult(VectorStoreResult vectorStoreResult) {
        this.vectorStoreResult = vectorStoreResult;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    /**
     * 获取切片数量
     */
    public int getChunkCount() {
        return chunks != null ? chunks.size() : 0;
    }

    /**
     * 判断向量入库是否成功
     */
    public boolean isVectorStored() {
        return vectorStoreResult != null && vectorStoreResult.isSuccess();
    }

    @Override
    public String toString() {
        return "SummarizationResult{" +
                "chunkCount=" + getChunkCount() +
                ", vectorStoreResult=" + vectorStoreResult +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }
}
