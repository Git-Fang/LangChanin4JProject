package org.fb.service;

import org.fb.bean.MarkdownBlock;

import java.util.List;

/**
 * Markdown语义切片服务接口
 * 用于将Markdown文档按语义块进行切分，保证语义完整性
 */
public interface MarkdownSemanticChunker {
    
    /**
     * 按语义块切分Markdown内容
     *
     * @param blocks MarkdownBlock列表
     * @return 切分后的文本列表
     */
    List<String> chunk(List<MarkdownBlock> blocks);
    
    /**
     * 将MarkdownBlock列表转换为文本
     *
     * @param blocks MarkdownBlock列表
     * @return 合并后的文本
     */
    String blocksToText(List<MarkdownBlock> blocks);
    
    /**
     * 智能切分文本，平衡块大小
     *
     * @param blocks MarkdownBlock列表
     * @param maxChunkSize 最大块大小（字符数）
     * @return 切分后的文本列表
     */
    List<String> smartChunk(List<MarkdownBlock> blocks, int maxChunkSize);
    
    /**
     * 判断是否应该合并两个块
     *
     * @param block1 前一个块
     * @param block2 后一个块
     * @return 是否应该合并
     */
    boolean shouldMerge(MarkdownBlock block1, MarkdownBlock block2);
    
    /**
     * 估算块的语义权重
     * 用于智能切分时考虑块的"重要性"
     *
     * @param block Markdown块
     * @return 权重值 (1.0为基础权重)
     */
    double estimateBlockWeight(MarkdownBlock block);
}
