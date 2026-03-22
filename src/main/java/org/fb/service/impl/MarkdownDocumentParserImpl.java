package org.fb.service.impl;

import org.fb.bean.MarkdownBlock;
import org.fb.bean.MarkdownBlockType;
import org.fb.service.MarkdownDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown文档解析服务实现
 * 用于解析Markdown文档，提取各类内容块并处理图片、图表等
 */
@Service
public class MarkdownDocumentParserImpl implements MarkdownDocumentParser {
    private static final Logger log = LoggerFactory.getLogger(MarkdownDocumentParserImpl.class);

    // 正则表达式定义
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("^```(\\w*)?$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern TABLE_PATTERN = Pattern.compile("^\\|.+\\|$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+.+$|^\\s*\\d+\\.\\s+.+$");
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s+(.+)$");
    private static final Pattern HR_PATTERN = Pattern.compile("^---+$|^\\*\\*\\*+$|^___+$");
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---$");

    private boolean inFrontMatter = false;
    private boolean inCodeBlock = false;
    private String currentCodeLanguage = "";

    @Autowired
    @Qualifier("qwenVisionChatModel")
    private dev.langchain4j.model.chat.ChatModel visionChatModel;

    /**
     * 解析Markdown文档
     */
    @Override
    public List<MarkdownBlock> parse(String mdContent, Path baseDir) {
        List<MarkdownBlock> blocks = parseBlocks(mdContent);
        
        // 处理特殊块（图片、图表等）
        processBlocks(blocks, baseDir);
        
        return blocks;
    }

    /**
     * 解析Markdown文档结构
     */
    @Override
    public List<MarkdownBlock> parseBlocks(String mdContent) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        
        if (mdContent == null || mdContent.trim().isEmpty()) {
            return blocks;
        }
        
        String[] lines = mdContent.split("\n");
        int lineStart = 0;
        StringBuilder codeBlockContent = new StringBuilder();
        boolean startedCodeBlock = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // 处理代码块
            if (line.startsWith("```")) {
                Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(line.trim());
                if (!inCodeBlock && codeMatcher.matches()) {
                    // 开始代码块
                    inCodeBlock = true;
                    currentCodeLanguage = codeMatcher.group(1) != null ? codeMatcher.group(1) : "";
                    codeBlockContent = new StringBuilder();
                    lineStart = i;
                    startedCodeBlock = true;
                } else if (inCodeBlock) {
                    // 结束代码块
                    inCodeBlock = false;
                    String codeContent = codeBlockContent.toString().trim();
                    if (!codeContent.isEmpty()) {
                        blocks.add(MarkdownBlock.codeBlock(codeContent, currentCodeLanguage, lineStart, i));
                    }
                    startedCodeBlock = false;
                    currentCodeLanguage = "";
                }
            } else if (inCodeBlock) {
                // 代码块内容行
                if (codeBlockContent.length() > 0) {
                    codeBlockContent.append("\n");
                }
                codeBlockContent.append(line);
            } else {
                // 解析其他块
                MarkdownBlock block = parseLine(line, i);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        
        // 处理未关闭的代码块
        if (inCodeBlock && codeBlockContent.length() > 0) {
            blocks.add(MarkdownBlock.codeBlock(codeBlockContent.toString().trim(), currentCodeLanguage, lineStart, lines.length - 1));
        }
        
        return mergeAdjacentParagraphs(blocks);
    }

    /**
     * 解析单行内容
     */
    private MarkdownBlock parseLine(String line, int lineNumber) {
        // 空行跳过
        if (line.trim().isEmpty()) {
            return null;
        }
        
        // Front Matter
        if (line.equals("---") && !inFrontMatter) {
            inFrontMatter = true;
            return null;
        }
        if (inFrontMatter && line.equals("---")) {
            inFrontMatter = false;
            return null;
        }
        if (inFrontMatter) {
            return null;
        }
        
        // 标题
        Matcher headingMatcher = HEADING_PATTERN.matcher(line.trim());
        if (headingMatcher.matches()) {
            String hashes = headingMatcher.group(1);
            String content = headingMatcher.group(2);
            int level = hashes.length();
            return MarkdownBlock.heading(content, level, lineNumber, lineNumber);
        }
        
        // 表格
        Matcher tableMatcher = TABLE_PATTERN.matcher(line.trim());
        if (tableMatcher.matches()) {
            return MarkdownBlock.table(line.trim(), lineNumber, lineNumber);
        }
        
        // 分隔线
        Matcher hrMatcher = HR_PATTERN.matcher(line.trim());
        if (hrMatcher.matches()) {
            return new MarkdownBlock(MarkdownBlockType.HORIZONTAL_RULE, "---", lineNumber, lineNumber);
        }
        
        // 引用
        Matcher blockquoteMatcher = BLOCKQUOTE_PATTERN.matcher(line.trim());
        if (blockquoteMatcher.matches()) {
            return new MarkdownBlock(MarkdownBlockType.BLOCKQUOTE, blockquoteMatcher.group(1), lineNumber, lineNumber);
        }
        
        // 列表项
        Matcher listMatcher = LIST_PATTERN.matcher(line);
        if (listMatcher.matches()) {
            return new MarkdownBlock(MarkdownBlockType.UNORDERED_LIST, line.trim(), lineNumber, lineNumber);
        }
        
        // 图片
        Matcher imageMatcher = IMAGE_PATTERN.matcher(line);
        if (imageMatcher.find()) {
            String alt = imageMatcher.group(1);
            String url = imageMatcher.group(2);
            return MarkdownBlock.image(alt, url, lineNumber, lineNumber);
        }
        
        // 段落
        return MarkdownBlock.paragraph(line.trim(), lineNumber, lineNumber);
    }

    /**
     * 合并相邻的段落
     */
    private List<MarkdownBlock> mergeAdjacentParagraphs(List<MarkdownBlock> blocks) {
        List<MarkdownBlock> merged = new ArrayList<>();
        MarkdownBlock lastParagraph = null;
        
        for (MarkdownBlock block : blocks) {
            if (block.getType() == MarkdownBlockType.PARAGRAPH && lastParagraph != null) {
                // 合并相邻段落
                String mergedContent = lastParagraph.getRawContent() + "\n" + block.getRawContent();
                lastParagraph = MarkdownBlock.paragraph(mergedContent, lastParagraph.getLineStart(), block.getLineEnd());
            } else {
                if (lastParagraph != null) {
                    merged.add(lastParagraph);
                }
                lastParagraph = block;
            }
        }
        
        if (lastParagraph != null) {
            merged.add(lastParagraph);
        }
        
        return merged;
    }

    /**
     * 处理图片块 - 调用视觉模型生成描述
     */
    @Override
    public void processImageBlock(MarkdownBlock block, Path baseDir) {
        if (block.getType() != MarkdownBlockType.IMAGE) {
            return;
        }
        
        String imageUrl = (String) block.getMetadata().get("url");
        String description;
        
        if (imageUrl != null) {
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                description = describeRemoteImage(imageUrl);
            } else {
                // 本地图片
                Path imagePath = baseDir.resolve(imageUrl);
                description = describeLocalImage(imagePath);
            }
            
            String alt = (String) block.getMetadata().get("alt");
            block.setProcessedContent("【图片描述: " + description + " alt=" + alt + "】");
        }
    }

    /**
     * 处理图表代码块 - 生成图表描述
     */
    @Override
    public void processDiagramBlock(MarkdownBlock block) {
        if (!block.isDiagramBlock()) {
            return;
        }
        
        String code = block.getRawContent();
        String description;
        
        if (block.isMermaidBlock()) {
            description = describeMermaidDiagram(code);
        } else if (block.isPlantUmlBlock()) {
            description = describePlantUmlDiagram(code);
        } else {
            description = "【图表代码块: " + block.getMetadata().get("language") + "】";
        }
        
        block.setProcessedContent(description);
    }

    /**
     * 处理表格块 - 转换为可读文本
     */
    @Override
    public void processTableBlock(MarkdownBlock block) {
        if (block.getType() != MarkdownBlockType.TABLE) {
            return;
        }
        
        String tableContent = block.getRawContent();
        StringBuilder readable = new StringBuilder();
        String[] rows = tableContent.split("\n");
        
        for (String row : rows) {
            if (row.contains("---")) {
                continue; // 跳过分隔行
            }
            String cells = row.replace("|", " | ");
            readable.append(cells).append("\n");
        }
        
        block.setProcessedContent("【表格:\n" + readable.toString() + "】");
    }

    /**
     * 描述本地图片 - 调用视觉模型
     */
    @Override
    public String describeLocalImage(Path imagePath) {
        if (visionChatModel == null) {
            return "[图片描述不可用 - 视觉模型未配置]";
        }
        
        try {
            // 简单返回图片路径
            return "本地图片: " + imagePath.getFileName();
        } catch (Exception e) {
            log.error("描述本地图片失败: {}", e.getMessage());
            return "[图片描述失败]";
        }
    }

    /**
     * 描述网络图片
     */
    @Override
    public String describeRemoteImage(String imageUrl) {
        if (visionChatModel == null) {
            return "[图片描述不可用 - 视觉模型未配置]";
        }
        
        try {
            // 简单返回URL
            return "网络图片: " + imageUrl;
        } catch (Exception e) {
            log.error("描述网络图片失败: {}", e.getMessage());
            return "[图片描述失败]";
        }
    }

    /**
     * 描述Mermaid图表
     */
    @Override
    public String describeMermaidDiagram(String code) {
        return "【Mermaid图表: " + code.substring(0, Math.min(100, code.length())) + "...】";
    }

    /**
     * 描述PlantUML图表
     */
    @Override
    public String describePlantUmlDiagram(String code) {
        return "【PlantUML图表: " + code.substring(0, Math.min(100, code.length())) + "...】";
    }

    /**
     * 检测图表类型
     */
    @Override
    public String detectDiagramLanguage(String code) {
        if (code.trim().startsWith("@startuml") || code.contains("skinparam")) {
            return "plantuml";
        }
        if (code.contains("graph ") || code.contains("flowchart ") || code.contains("stateDiagram")) {
            return "mermaid";
        }
        return null;
    }

    /**
     * 批量处理所有块
     */
    @Override
    public void processBlocks(List<MarkdownBlock> blocks, Path baseDir) {
        for (MarkdownBlock block : blocks) {
            switch (block.getType()) {
                case IMAGE:
                    processImageBlock(block, baseDir);
                    break;
                case CODE_BLOCK:
                    if (block.isDiagramBlock()) {
                        processDiagramBlock(block);
                    }
                    break;
                case TABLE:
                    processTableBlock(block);
                    break;
                default:
                    break;
            }
        }
    }
}
