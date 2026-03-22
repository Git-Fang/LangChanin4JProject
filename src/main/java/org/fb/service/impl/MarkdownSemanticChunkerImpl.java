package org.fb.service.impl;

import org.fb.bean.MarkdownBlock;
import org.fb.bean.MarkdownBlockType;
import org.fb.service.MarkdownSemanticChunker;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown语义切片服务实现
 * 用于将Markdown文档按语义块进行切分，保证语义完整性
 */
@Service
public class MarkdownSemanticChunkerImpl implements MarkdownSemanticChunker {
    
    /**
     * 默认最大块大小（字符数）
     */
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1024;
    
    /**
     * 最小块大小（字符数）
     */
    private static final int MIN_CHUNK_SIZE = 200;

    /**
     * 按语义块切分Markdown内容
     */
    @Override
    public List<String> chunk(List<MarkdownBlock> blocks) {
        return smartChunk(blocks, DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * 将MarkdownBlock列表转换为文本
     */
    @Override
    public String blocksToText(List<MarkdownBlock> blocks) {
        StringBuilder text = new StringBuilder();
        
        for (MarkdownBlock block : blocks) {
            String content = block.getProcessedContent();
            
            if (content == null || content.trim().isEmpty()) {
                continue;
            }
            
            // 根据块类型添加适当的分隔
            switch (block.getType()) {
                case HEADING:
                    text.append("\n## ").append(content).append("\n");
                    break;
                case CODE_BLOCK:
                    String language = (String) block.getMetadata().get("language");
                    text.append("\n```").append(language != null ? language : "").append("\n");
                    text.append(content).append("\n```\n");
                    break;
                case TABLE:
                    text.append("\n").append(content).append("\n");
                    break;
                case ORDERED_LIST:
                case UNORDERED_LIST:
                    text.append("- ").append(content).append("\n");
                    break;
                case BLOCKQUOTE:
                    text.append("> ").append(content).append("\n");
                    break;
                case IMAGE:
                    text.append(content).append("\n");
                    break;
                case HORIZONTAL_RULE:
                    text.append("\n---\n");
                    break;
                default:
                    text.append(content).append("\n");
            }
        }
        
        return text.toString();
    }

    /**
     * 智能切分文本，平衡块大小
     */
    @Override
    public List<String> smartChunk(List<MarkdownBlock> blocks, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        
        if (blocks == null || blocks.isEmpty()) {
            return chunks;
        }
        
        StringBuilder currentChunk = new StringBuilder();
        double currentWeight = 0;
        
        for (MarkdownBlock block : blocks) {
            double blockWeight = estimateBlockWeight(block);
            int blockSize = block.getProcessedContent() != null ? block.getProcessedContent().length() : 0;
            
            // 判断是否应该合并
            if (shouldMerge(getLastBlockFromChunks(chunks), block)) {
                // 计算合并后的预估大小
                String mergedContent = currentChunk.toString() + "\n" + block.getProcessedContent();
                
                if (mergedContent.length() <= maxChunkSize) {
                    // 可以合并
                    currentChunk.append("\n").append(block.getProcessedContent());
                    currentWeight += blockWeight;
                } else {
                    // 保存当前块，开始新块
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                    }
                    currentChunk = new StringBuilder(block.getProcessedContent());
                    currentWeight = blockWeight;
                }
            } else {
                // 不应该合并（标题级别变化、主题切换等）
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder(block.getProcessedContent());
                currentWeight = blockWeight;
            }
            
            // 如果当前块已经很大，检查是否需要切分
            while (currentChunk.length() > maxChunkSize) {
                String chunkContent = currentChunk.toString();
                List<String> subChunks = splitLargeChunk(chunkContent, maxChunkSize);
                
                if (subChunks.size() > 1) {
                    chunks.addAll(subChunks.subList(0, subChunks.size() - 1));
                    currentChunk = new StringBuilder(subChunks.get(subChunks.size() - 1));
                } else {
                    break;
                }
            }
        }
        
        // 添加最后一个块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }

    /**
     * 获取最后一个块
     */
    private MarkdownBlock getLastBlockFromChunks(List<String> chunks) {
        // 简化实现：返回null表示总是可以合并
        return null;
    }

    /**
     * 切分过大的块
     */
    private List<String> splitLargeChunk(String content, int maxSize) {
        List<String> result = new ArrayList<>();
        
        if (content.length() <= maxSize) {
            result.add(content);
            return result;
        }
        
        // 按段落和句子切分
        String[] paragraphs = content.split("\n");
        StringBuilder current = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 1 > maxSize) {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                
                // 如果单个段落仍然太大，按句子切分
                if (paragraph.length() > maxSize) {
                    List<String> sentences = splitBySentence(paragraph, maxSize);
                    for (int i = 0; i < sentences.size(); i++) {
                        if (i < sentences.size() - 1) {
                            result.add(sentences.get(i));
                        } else {
                            current.append(sentences.get(i));
                        }
                    }
                } else {
                    current.append(paragraph);
                }
            } else {
                if (current.length() > 0) {
                    current.append("\n");
                }
                current.append(paragraph);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        
        return result;
    }

    /**
     * 按句子切分
     */
    private List<String> splitBySentence(String text, int maxSize) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("[。！？.!?]");
        StringBuilder current = new StringBuilder();
        
        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 > maxSize) {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                
                // 如果单个句子仍然太大，按字符切分
                if (sentence.length() > maxSize) {
                    for (int i = 0; i < sentence.length(); i += maxSize) {
                        int end = Math.min(i + maxSize, sentence.length());
                        result.add(sentence.substring(i, end));
                    }
                } else {
                    current.append(sentence);
                }
            } else {
                if (current.length() > 0) {
                    current.append("。");
                }
                current.append(sentence);
            }
        }
        
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        
        return result;
    }

    /**
     * 判断是否应该合并两个块
     */
    @Override
    public boolean shouldMerge(MarkdownBlock block1, MarkdownBlock block2) {
        if (block1 == null || block2 == null) {
            return true;
        }
        
        MarkdownBlockType type1 = block1.getType();
        MarkdownBlockType type2 = block2.getType();
        
        // 标题后面不应该直接合并内容
        if (type1 == MarkdownBlockType.HEADING) {
            return false;
        }
        
        // 分隔线后不应该合并
        if (type1 == MarkdownBlockType.HORIZONTAL_RULE || type2 == MarkdownBlockType.HORIZONTAL_RULE) {
            return false;
        }
        
        // 代码块不应该与其他块合并
        if (type1 == MarkdownBlockType.CODE_BLOCK || type2 == MarkdownBlockType.CODE_BLOCK) {
            return false;
        }
        
        // 表格不应该与其他块合并
        if (type1 == MarkdownBlockType.TABLE || type2 == MarkdownBlockType.TABLE) {
            return false;
        }
        
        // 相同标题级别的标题之间不应该合并
        if (type1 == MarkdownBlockType.HEADING && type2 == MarkdownBlockType.HEADING) {
            return block1.getHeadingLevel() >= block2.getHeadingLevel();
        }
        
        // 引用块可以合并
        if (type1 == MarkdownBlockType.BLOCKQUOTE && type2 == MarkdownBlockType.BLOCKQUOTE) {
            return true;
        }
        
        // 列表项可以合并
        if ((type1 == MarkdownBlockType.ORDERED_LIST || type1 == MarkdownBlockType.UNORDERED_LIST) 
                && (type2 == MarkdownBlockType.ORDERED_LIST || type2 == MarkdownBlockType.UNORDERED_LIST)) {
            return true;
        }
        
        // 段落可以合并
        return type1 == MarkdownBlockType.PARAGRAPH && type2 == MarkdownBlockType.PARAGRAPH;
    }

    /**
     * 估算块的语义权重
     */
    @Override
    public double estimateBlockWeight(MarkdownBlock block) {
        if (block == null) {
            return 1.0;
        }
        
        double baseWeight = 1.0;
        MarkdownBlockType type = block.getType();
        
        switch (type) {
            case HEADING:
                // 标题权重高，尤其是高级别标题
                baseWeight = 1.5 + (block.getHeadingLevel() * 0.5);
                break;
            case CODE_BLOCK:
                // 代码块权重较高
                baseWeight = 2.0;
                break;
            case TABLE:
                // 表格权重很高
                baseWeight = 2.5;
                break;
            case ORDERED_LIST:
            case UNORDERED_LIST:
                // 列表项权重中等
                baseWeight = 1.2;
                break;
            case IMAGE:
                // 图片权重中等
                baseWeight = 1.3;
                break;
            case BLOCKQUOTE:
                // 引用权重较低
                baseWeight = 0.8;
                break;
            case PARAGRAPH:
            default:
                baseWeight = 1.0;
                break;
        }
        
        // 根据内容长度调整权重
        int contentLength = block.getProcessedContent() != null ? block.getProcessedContent().length() : 0;
        if (contentLength > 500) {
            baseWeight *= 1.2;
        } else if (contentLength < 100) {
            baseWeight *= 0.8;
        }
        
        return baseWeight;
    }
}
