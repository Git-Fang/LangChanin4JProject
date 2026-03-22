package org.fb.bean;

import java.io.Serializable;

/**
 * 向量入库结果
 * 封装文档切片向量入库的状态和信息
 */
public class VectorStoreResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否入库成功
     */
    private boolean success;

    /**
     * collection名称
     */
    private String collectionName;

    /**
     * 入库切片数量
     */
    private int chunkCount;

    /**
     * 入库时间（毫秒）
     */
    private long storedTimeMs;

    /**
     * 错误信息（如果入库失败）
     */
    private String errorMessage;

    /**
     * 切片ID列表（Qdrant中的point ID）
     */
    private String[] pointIds;

    public VectorStoreResult() {
    }

    /**
     * 创建成功结果
     */
    public static VectorStoreResult success(String collectionName, int chunkCount, long storedTimeMs, String[] pointIds) {
        VectorStoreResult result = new VectorStoreResult();
        result.success = true;
        result.collectionName = collectionName;
        result.chunkCount = chunkCount;
        result.storedTimeMs = storedTimeMs;
        result.pointIds = pointIds;
        return result;
    }

    /**
     * 创建失败结果
     */
    public static VectorStoreResult failure(String collectionName, String errorMessage) {
        VectorStoreResult result = new VectorStoreResult();
        result.success = false;
        result.collectionName = collectionName;
        result.errorMessage = errorMessage;
        return result;
    }

    /**
     * 创建跳过的结果（当没有切片需要入库时）
     */
    public static VectorStoreResult skipped(String collectionName) {
        VectorStoreResult result = new VectorStoreResult();
        result.success = true;
        result.collectionName = collectionName;
        result.chunkCount = 0;
        result.storedTimeMs = 0;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public long getStoredTimeMs() {
        return storedTimeMs;
    }

    public void setStoredTimeMs(long storedTimeMs) {
        this.storedTimeMs = storedTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String[] getPointIds() {
        return pointIds;
    }

    public void setPointIds(String[] pointIds) {
        this.pointIds = pointIds;
    }

    /**
     * 获取可读的入库状态描述
     */
    public String getStatusDescription() {
        if (success) {
            if (chunkCount == 0) {
                return "跳过入库（无切片数据）";
            }
            return String.format("成功入库 %d 个切片到 collection '%s' (耗时 %dms)", 
                    chunkCount, collectionName, storedTimeMs);
        } else {
            return String.format("入库失败: %s", errorMessage != null ? errorMessage : "未知错误");
        }
    }

    @Override
    public String toString() {
        return "VectorStoreResult{" +
                "success=" + success +
                ", collectionName='" + collectionName + '\'' +
                ", chunkCount=" + chunkCount +
                ", storedTimeMs=" + storedTimeMs +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
