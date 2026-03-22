package org.fb.service.impl;

import org.fb.bean.MarkdownBlock;
import org.fb.bean.MarkdownBlockType;
import org.fb.service.MarkdownSemanticChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown语义切片服务实现
 * 按语义块进行切分，保证语义完整性
 */
@Service
public class MarkdownSemanticChunkerImpl implements MarkdownSemanticChunker {
    private static final Logger log = LoggerFactory.getLogger(MarkdownSemanticChunkerImpl.class);
    
    /**
     * 默认最大块大小（字符数）
     */
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024;
    
    /**
     * 最小块大小阈值（用于合并小型相关块）
     */
    private static final int MIN_CHUNK_SIZE_THRESHOLD = 200;
    
    /**
     * 大型块大小阈值（超过此值需要特殊处理）
     */
    private static final int LARGE_BLOCK_THRESHOLD = 1500;
    
    @Override
    public List<String> chunk(List<MarkdownBlock> blocks) {
        return smartChunk(blocks, DEFAULT_MAX_CHUNK_SIZE);
    }
    
    @Override
    public String blocksToText(List<MarkdownBlock> blocks) {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < blocks.size(); i++) {
            MarkdownBlock block = blocks.get(i);
            String content = block.getProcessedContent();
            
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            
            // 跳过空行块
            if (block.getType() == MarkdownBlockType.EMPTY) {
                continue;
            }
            
            // 标题块前后添加空行
            if (block.getType() == MarkdownBlockType.HEADING) {
                if (result.length() > 0 && !result.toString().endsWith("\n\n")) {
                    result.append("\n\n");
                }
            }
            
            result.append(content);
            
            // 非最后一块添加分隔
            if (i < blocks.size() - 1) {
                MarkdownBlock nextBlock = blocks.get(i + 1);
                if (nextBlock.getType() != MarkdownBlockType.EMPTY) {
                    // 不同类型的块之间添加分隔
                    if (!shouldAppendWithoutSeparator(block.getType(), nextBlock.getType())) {
                        result.append("\n\n");
                    } else {
                        result.append("\n");
                    }
                }
            }
        }
        
        return result.toString().trim();
    }
    
    @Override
    public List<String> smartChunk(List<MarkdownBlock> blocks, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            return chunks;
        }
        
        StringBuilder currentChunk = new StringBuilder();
        List<MarkdownBlock> currentChunkBlocks = new ArrayList<>();
        
        for (MarkdownBlock block : blocks) {
            // 跳过空行块
            if (block.getType() == MarkdownBlockType.EMPTY) {
                continue;
            }
            
            String blockContent = block.getProcessedContent();
            if (blockContent == null || blockContent.trim().isEmpty()) {
                continue;
            }
            
            int blockLength = blockContent.length();
            
            // 处理大型语义块 (代码块、表格)
            if (block.isLargeBlock()) {
                // 1. 先保存当前块
                if (currentChunk.length() > 0) {
                    String currentText = currentChunk.toString().trim();
                    if (!currentText.isEmpty()) {
                        chunks.add(currentText);
                    }
                    currentChunk = new StringBuilder();
                    currentChunkBlocks.clear();
                }
                
                // 2. 处理大型块
                if (blockLength <= maxChunkSize * 1.5) {
                    // 大小可接受，作为独立块
                    chunks.add(blockContent);
                } else {
                    // 超大块需要拆分
                    List<String> splitParts = splitLargeBlock(block, maxChunkSize);
                    chunks.addAll(splitParts);
                }
                continue;
            }
            
            // 处理标题块
            if (block.getType() == MarkdownBlockType.HEADING) {
                // 如果当前块已有内容，检查是否应该在新切片开始
                if (currentChunk.length() > 0) {
                    String currentText = currentChunk.toString().trim();
                    
                    // 如果当前块接近满载，或者标题级别更高(数字更小)，开始新块
                    if (currentText.length() >= maxChunkSize * 0.7 || 
                        block.getHeadingLevel() <= getLastHeadingLevel(currentChunkBlocks)) {
                        chunks.add(currentText);
                        currentChunk = new StringBuilder();
                        currentChunkBlocks.clear();
                    }
                }
                
                // 添加标题
                if (currentChunk.length() > 0 && !currentChunk.toString().endsWith("\n\n")) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(blockContent);
                currentChunkBlocks.add(block);
                continue;
            }
            
            // 处理列表项
            if (block.getType() == MarkdownBlockType.ORDERED_LIST || 
                block.getType() == MarkdownBlockType.UNORDERED_LIST) {
                
                int currentLength = currentChunk.length();
                int addedLength = blockLength + (currentLength > 0 ? 2 : 0);
                
                // 检查是否超出限制
                if (currentLength + addedLength > maxChunkSize) {
                    // 保存当前块
                    String currentText = currentChunk.toString().trim();
                    if (!currentText.isEmpty()) {
                        chunks.add(currentText);
                    }
                    currentChunk = new StringBuilder();
                    currentChunkBlocks.clear();
                }
                
                // 添加列表项
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(blockContent);
                currentChunkBlocks.add(block);
                continue;
            }
            
            // 处理普通段落
            int currentLength = currentChunk.length();
            int addedLength = blockLength + (currentLength > 0 ? 2 : 0);
            
            // 检查是否超出限制
            if (currentLength + addedLength > maxChunkSize) {
                // 当前块已满，保存
                String currentText = currentChunk.toString().trim();
                if (!currentText.isEmpty()) {
                    chunks.add(currentText);
                }
                
                // 检查单个段落是否太大
                if (blockLength > maxChunkSize) {
                    // 按句子拆分大段落
                    List<String> splitParagraphs = splitLargeParagraph(blockContent, maxChunkSize);
                    for (int i = 0; i < splitParagraphs.size(); i++) {
                        chunks.add(splitParagraphs.get(i));
                    }
                    currentChunk = new StringBuilder();
                    currentChunkBlocks.clear();
                } else {
                    // 从当前段落开始新块
                    currentChunk = new StringBuilder(blockContent);
                    currentChunkBlocks.clear();
                    currentChunkBlocks.add(block);
                }
            } else {
                // 添加到当前块
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(blockContent);
                currentChunkBlocks.add(block);
            }
        }
        
        // 处理最后一个块
        if (currentChunk.length() > 0) {
            String lastText = currentChunk.toString().trim();
            if (!lastText.isEmpty()) {
                // 如果最后一个块太大，尝试按句子再拆分
                if (lastText.length() > maxChunkSize * 1.3) {
                    List<String> splitParts = splitLargeText(lastText, maxChunkSize);
                    chunks.addAll(splitParts);
                } else {
                    chunks.add(lastText);
                }
            }
        }
        
        return chunks;
    }
    
    @Override
    public boolean shouldMerge(MarkdownBlock block1, MarkdownBlock block2) {
        if (block1 == null || block2 == null) {
            return false;
        }
        
        MarkdownBlockType type1 = block1.getType();
        MarkdownBlockType type2 = block2.getType();
        
        // 相同类型的列表项应该合并
        if (type1 == MarkdownBlockType.ORDERED_LIST && type2 == MarkdownBlockType.ORDERED_LIST) {
            return true;
        }
        if (type1 == MarkdownBlockType.UNORDERED_LIST && type2 == MarkdownBlockType.UNORDERED_LIST) {
            return true;
        }
        
        // 标题和紧跟的段落可以合并
        if (type1 == MarkdownBlockType.HEADING && type2 == MarkdownBlockType.PARAGRAPH) {
            return true;
        }
        
        // 引用块可以合并
        if (type1 == MarkdownBlockType.BLOCKQUOTE && type2 == MarkdownBlockType.BLOCKQUOTE) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public double estimateBlockWeight(MarkdownBlock block) {
        if (block == null) {
            return 1.0;
        }
        
        MarkdownBlockType type = block.getType();
        double weight = 1.0;
        
        switch (type) {
            case HEADING:
                // 标题权重较高，尤其是高级标题
                weight = 1.5 + (6 - block.getHeadingLevel()) * 0.3;
                break;
            case CODE_BLOCK:
                // 代码块权重高
                weight = 2.0;
                break;
            case TABLE:
                // 表格权重高
                weight = 1.8;
                break;
            case IMAGE:
                // 图片描述权重高
                weight = 1.5;
                break;
            case BLOCKQUOTE:
                // 引用块权重中等
                weight = 1.2;
                break;
            case PARAGRAPH:
                // 普通段落基础权重
                weight = 1.0;
                break;
            case ORDERED_LIST:
            case UNORDERED_LIST:
                // 列表项权重略低
                weight = 0.8;
                break;
            default:
                weight = 1.0;
        }
        
        // 根据内容长度调整
        int contentLength = block.getContentLength();
        if (contentLength > 500) {
            weight *= 1.2;
        } else if (contentLength < 50) {
            weight *= 0.8;
        }
        
        return weight;
    }
    
    /**
     * 拆分大型块
     */
    private List<String> splitLargeBlock(MarkdownBlock block, int maxSize) {
        List<String> parts = new ArrayList<>();
        String content = block.getProcessedContent();
        
        if (content.length() <= maxSize * 2) {
            // 只稍微超出，截断并添加标记
            parts.add(content.substring(0, maxSize * 2) + "\n...[内容已截断]...");
        } else {
            // 严重超出，分段保留关键信息
            String prefix = content.substring(0, Math.min(maxSize, content.length() / 4));
            String suffix = content.substring(Math.max(0, content.length() - maxSize / 4));
            
            String typeInfo = "";
            if (block.isMermaidBlock()) {
                typeInfo = "[Mermaid图表代码块] ";
            } else if (block.isPlantUmlBlock()) {
                typeInfo = "[PlantUML图表代码块] ";
            } else {
                typeInfo = "[" + block.getMetadata().get("language") + "代码块] ";
            }
            
            parts.add(typeInfo + prefix + "\n...[中间内容省略]...\n" + suffix);
        }
        
        return parts;
    }
    
    /**
     * 拆分大段落
     */
    private List<String> splitLargeParagraph(String content, int maxSize) {
        List<String> parts = new ArrayList<>();
        
        // 按句子分割
        String[] sentences = content.split("[。！？.!?]+");
        StringBuilder currentPart = new StringBuilder();
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            
            // 确保句子以标点结尾
            if (!sentence.matches(".*[。！？.!?]$")) {
                sentence += "。";
            }
            
            if (currentPart.length() + sentence.length() + 1 > maxSize) {
                // 当前部分已满，保存并开始新部分
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString().trim());
                    currentPart = new StringBuilder();
                }
                
                // 如果单个句子太长，按字数截断
                if (sentence.length() > maxSize) {
                    int start = 0;
                    while (start < sentence.length()) {
                        int end = Math.min(start + maxSize, sentence.length());
                        parts.add(sentence.substring(start, end));
                        start = end;
                    }
                } else {
                    currentPart.append(sentence);
                }
            } else {
                if (currentPart.length() > 0) {
                    currentPart.append("。");
                }
                currentPart.append(sentence);
            }
        }
        
        // 处理最后一部分
        if (currentPart.length() > 0) {
            String lastPart = currentPart.toString().trim();
            if (!lastPart.isEmpty()) {
                parts.add(lastPart);
            }
        }
        
        return parts;
    }
    
    /**
     * 拆分大型文本
     */
    private List<String> splitLargeText(String text, int maxSize) {
        List<String> parts = new ArrayList<>();
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            
            // 尽量在段落或句子边界切分
            if (end < text.length()) {
                // 向前查找最近的段落分隔
                int breakPoint = findBreakPoint(text, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        
        return parts;
    }
    
    /**
     * 查找合适的断点
     */
    private int findBreakPoint(String text, int start, int end) {
        // 向前查找最近的段落分隔或句子分隔
        for (int i = end - 1; i >= start + max(0, end - 200); i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || 
                c == '.' || c == '!' || c == '?' || c == ';') {
                return i + 1;
            }
        }
        return end;
    }
    
    /**
     * 获取最后一个标题的级别
     */
    private int getLastHeadingLevel(List<MarkdownBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            MarkdownBlock block = blocks.get(i);
            if (block.getType() == MarkdownBlockType.HEADING) {
                return block.getHeadingLevel();
            }
        }
        return 7; // 默认最大级别
    }
    
    /**
     * 判断是否应该添加分隔符
     */
    private boolean shouldAppendWithoutSeparator(MarkdownBlockType type1, MarkdownBlockType type2) {
        // 列表项之间用换行分隔
        if ((type1 == MarkdownBlockType.ORDERED_LIST || type1 == MarkdownBlockType.UNORDERED_LIST) &&
            (type2 == MarkdownBlockType.ORDERED_LIST || type2 == MarkdownBlockType.UNORDERED_LIST)) {
            return true;
        }
        
        // 标题和紧随的内容之间用换行分隔
        if (type1 == MarkdownBlockType.HEADING) {
            return true;
        }
        
        return false;
    }
    
    private int max(int a, int b) {
        return a > b ? a : b;
    }
}
