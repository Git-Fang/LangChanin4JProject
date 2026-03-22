package org.fb.service.impl;

import dev.langchain4j.model.chat.ChatModel;
import org.fb.bean.MarkdownBlock;
import org.fb.bean.MarkdownBlockType;
import org.fb.service.MarkdownDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown文档解析服务实现
 * 支持解析图片、表格、Mermaid图表、PlantUML图表等
 */
@Service
public class MarkdownDocumentParserImpl implements MarkdownDocumentParser {
    private static final Logger log = LoggerFactory.getLogger(MarkdownDocumentParserImpl.class);
    
    /**
     * 图片正则: ![alt](url)
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    
    /**
     * 行内代码正则: `code`
     */
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    
    /**
     * 代码块开始正则: ```language
     */
    private static final Pattern CODE_BLOCK_START_PATTERN = Pattern.compile("^\\s*```(\\w*)");
    
    /**
     * 代码块结束正则: ```
     */
    private static final Pattern CODE_BLOCK_END_PATTERN = Pattern.compile("^\\s*```\\s*$");
    
    /**
     * 标题正则: # ## ### 等
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    
    /**
     * 无序列表正则: - 或 * 或 +
     */
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$");
    
    /**
     * 有序列表正则: 1. 2. 等
     */
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$");
    
    /**
     * 引用块正则: >
     */
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^\\s*>\\s*(.*)$");
    
    /**
     * 分隔线正则: --- 或 *** 或 ___
     */
    private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("^\\s*(?:---|-{3,}|\\*{3,}|_{3,})\\s*$");
    
    /**
     * 表格行正则
     */
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.+\\|\\s*$");
    
    /**
     * PlantUML开始标记
     */
    private static final String PLANTUML_START = "@startuml";
    
    /**
     * PlantUML结束标记
     */
    private static final String PLANTUML_END = "@enduml";
    
    /**
     * 视觉模型 (用于图片描述)
     */
    private ChatModel visionModel;
    
    /**
     * 默认视觉模型描述提示词
     */
    private static final String IMAGE_DESCRIPTION_PROMPT = 
        "请详细描述这张图片的内容，用于文档摘要。请用简洁的中文概括图片的主要信息，包括：\n" +
        "1. 图片的主题或类型\n" +
        "2. 主要内容或元素\n" +
        "3. 任何重要的文字或数据\n" +
        "4. 整体含义或结论\n" +
        "请直接输出描述，不要添加额外解释。";
    
    /**
     * 默认图表描述提示词
     */
    private static final String DIAGRAM_DESCRIPTION_PROMPT = 
        "请分析并描述这个图表，用于文档摘要。请说明：\n" +
        "1. 图表类型和用途\n" +
        "2. 主要节点或组件\n" +
        "3. 之间的关系或流程\n" +
        "4. 关键信息或结论\n" +
        "请直接输出描述，不要添加额外解释。";
    
    /**
     * 设置视觉模型
     */
    @Autowired(required = false)
    @Qualifier("qwenVisionChatModel")
    public void setVisionModel(ChatModel visionModel) {
        this.visionModel = visionModel;
    }
    
    @Override
    public List<MarkdownBlock> parse(String mdContent, Path baseDir) {
        // 1. 解析块结构
        List<MarkdownBlock> blocks = parseBlocks(mdContent);
        
        // 2. 批量处理特殊块
        processBlocks(blocks, baseDir);
        
        return blocks;
    }
    
    @Override
    public List<MarkdownBlock> parseBlocks(String mdContent) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        if (mdContent == null || mdContent.isEmpty()) {
            return blocks;
        }
        
        String[] lines = mdContent.split("\\r?\\n");
        int i = 0;
        boolean inCodeBlock = false;
        int codeBlockStartLine = 0;
        StringBuilder codeBlockContent = new StringBuilder();
        String codeBlockLanguage = "";
        
        while (i < lines.length) {
            String line = lines[i];
            
            // 处理代码块
            if (!inCodeBlock) {
                Matcher codeBlockStartMatcher = CODE_BLOCK_START_PATTERN.matcher(line);
                if (codeBlockStartMatcher.matches()) {
                    inCodeBlock = true;
                    codeBlockStartLine = i;
                    codeBlockLanguage = codeBlockStartMatcher.group(1);
                    codeBlockContent = new StringBuilder();
                    i++;
                    continue;
                }
            } else {
                Matcher codeBlockEndMatcher = CODE_BLOCK_END_PATTERN.matcher(line);
                if (codeBlockEndMatcher.matches()) {
                    // 代码块结束
                    String code = codeBlockContent.toString().trim();
                    
                    // 检测PlantUML
                    if (code.contains(PLANTUML_START)) {
                        codeBlockLanguage = "plantuml";
                    }
                    
                    MarkdownBlock block = MarkdownBlock.codeBlock(code, codeBlockLanguage, codeBlockStartLine, i);
                    blocks.add(block);
                    
                    inCodeBlock = false;
                    codeBlockContent = new StringBuilder();
                    codeBlockLanguage = "";
                } else {
                    if (codeBlockContent.length() > 0) {
                        codeBlockContent.append("\n");
                    }
                    codeBlockContent.append(line);
                }
                i++;
                continue;
            }
            
            // 处理表格 (多行)
            if (isTableRow(line)) {
                StringBuilder tableContent = new StringBuilder();
                int tableStartLine = i;
                
                while (i < lines.length && isTableRow(lines[i])) {
                    if (tableContent.length() > 0) {
                        tableContent.append("\n");
                    }
                    tableContent.append(lines[i]);
                    i++;
                }
                
                MarkdownBlock block = MarkdownBlock.table(tableContent.toString(), tableStartLine, i - 1);
                blocks.add(block);
                continue;
            }
            
            // 处理其他块
            MarkdownBlock block = parseSingleLine(line, i);
            if (block != null) {
                blocks.add(block);
            }
            
            i++;
        }
        
        // 处理未关闭的代码块
        if (inCodeBlock && codeBlockContent.length() > 0) {
            MarkdownBlock block = MarkdownBlock.codeBlock(codeBlockContent.toString().trim(), codeBlockLanguage, codeBlockStartLine, lines.length - 1);
            blocks.add(block);
        }
        
        return blocks;
    }
    
    /**
     * 解析单行内容
     */
    private MarkdownBlock parseSingleLine(String line, int lineIndex) {
        // 空行
        if (line.trim().isEmpty()) {
            return new MarkdownBlock(MarkdownBlockType.EMPTY, "", lineIndex, lineIndex);
        }
        
        // 标题
        Matcher headingMatcher = HEADING_PATTERN.matcher(line);
        if (headingMatcher.matches()) {
            String hashes = headingMatcher.group(1);
            String content = headingMatcher.group(2);
            MarkdownBlock block = MarkdownBlock.heading(content, hashes.length(), lineIndex, lineIndex);
            return block;
        }
        
        // 无序列表
        Matcher ulMatcher = UNORDERED_LIST_PATTERN.matcher(line);
        if (ulMatcher.matches()) {
            MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.UNORDERED_LIST, ulMatcher.group(1), lineIndex, lineIndex);
            block.setIndentLevel(countLeadingSpaces(line) / 2);
            return block;
        }
        
        // 有序列表
        Matcher olMatcher = ORDERED_LIST_PATTERN.matcher(line);
        if (olMatcher.matches()) {
            MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.ORDERED_LIST, olMatcher.group(1), lineIndex, lineIndex);
            block.setIndentLevel(countLeadingSpaces(line) / 2);
            return block;
        }
        
        // 引用块
        Matcher bqMatcher = BLOCKQUOTE_PATTERN.matcher(line);
        if (bqMatcher.matches()) {
            MarkdownBlock block = new MarkdownBlock(MarkdownBlockType.BLOCKQUOTE, bqMatcher.group(1), lineIndex, lineIndex);
            return block;
        }
        
        // 分隔线
        Matcher hrMatcher = HORIZONTAL_RULE_PATTERN.matcher(line);
        if (hrMatcher.matches()) {
            return new MarkdownBlock(MarkdownBlockType.HORIZONTAL_RULE, line, lineIndex, lineIndex);
        }
        
        // 检查是否包含图片
        Matcher imageMatcher = IMAGE_PATTERN.matcher(line);
        if (imageMatcher.find()) {
            String alt = imageMatcher.group(1);
            String url = imageMatcher.group(2);
            // 如果行中只有图片，返回图片块
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("![") && trimmedLine.endsWith(")")) {
                return MarkdownBlock.image(alt, url, lineIndex, lineIndex);
            }
        }
        
        // 普通段落
        return MarkdownBlock.paragraph(line, lineIndex, lineIndex);
    }
    
    /**
     * 判断是否为表格行
     */
    private boolean isTableRow(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
            return false;
        }
        // 排除分隔行 |---|---|
        return !trimmed.matches("\\|\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)*\\|?");
    }
    
    /**
     * 计算前导空格数
     */
    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 4;
            } else {
                break;
            }
        }
        return count;
    }
    
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
    
    @Override
    public void processImageBlock(MarkdownBlock block, Path baseDir) {
        String url = (String) block.getMetadata().get("url");
        String alt = (String) block.getMetadata().get("alt");
        
        if (url == null || url.isEmpty()) {
            block.setProcessedContent("[图片: 无URL]");
            return;
        }
        
        String description;
        if (isLocalPath(url)) {
            // 本地图片
            Path imagePath = baseDir.resolve(url.replaceFirst("^/", ""));
            description = describeLocalImage(imagePath);
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            // 网络图片
            description = describeRemoteImage(url);
        } else {
            // 相对路径
            Path imagePath = baseDir.resolve(url);
            description = describeLocalImage(imagePath);
        }
        
        String altText = alt != null && !alt.isEmpty() ? alt : "无描述";
        block.setProcessedContent(String.format("[图片: %s] %s", altText, description));
    }
    
    @Override
    public void processDiagramBlock(MarkdownBlock block) {
        String code = block.getRawContent();
        String language = (String) block.getMetadata().get("language");
        
        if (language == null) {
            language = detectDiagramLanguage(code);
            block.getMetadata().put("language", language);
        }
        
        String description;
        if ("mermaid".equalsIgnoreCase(language)) {
            description = describeMermaidDiagram(code);
        } else if ("plantuml".equalsIgnoreCase(language)) {
            description = describePlantUmlDiagram(code);
        } else {
            description = "[图表代码块: " + language + "]";
        }
        
        // 保留代码并添加描述
        block.setProcessedContent(description + "\n```" + language + "\n" + code + "\n```");
    }
    
    @Override
    public void processTableBlock(MarkdownBlock block) {
        String tableText = convertTableToText(block.getRawContent());
        block.setProcessedContent(tableText);
    }
    
    /**
     * 判断是否为本地路径
     */
    private boolean isLocalPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 绝对路径或相对路径
        return path.startsWith("/") || 
               path.matches("^[a-zA-Z]:\\\\.*") ||  // Windows绝对路径
               !path.contains("://");               // 非URL
    }
    
    @Override
    public String describeLocalImage(Path imagePath) {
        if (visionModel == null) {
            log.warn("视觉模型不可用，使用默认描述");
            return "[视觉模型不可用，无法描述图片]";
        }
        
        if (!Files.exists(imagePath)) {
            log.warn("本地图片文件不存在: {}", imagePath);
            return "[图片文件不存在: " + imagePath.getFileName() + "]";
        }
        
        try {
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            
            // 使用视觉模型描述图片
            String prompt = IMAGE_DESCRIPTION_PROMPT + "\n[图片数据: data:image/png;base64," + base64 + "]";
            String description = visionModel.chat(prompt);
            
            return description != null ? description.trim() : "[图片描述生成失败]";
            
        } catch (IOException e) {
            log.error("读取本地图片失败: {}", imagePath, e);
            return "[读取图片失败: " + e.getMessage() + "]";
        } catch (Exception e) {
            log.error("描述本地图片失败: {}", imagePath, e);
            return "[图片描述失败: " + e.getMessage() + "]";
        }
    }
    
    @Override
    public String describeRemoteImage(String imageUrl) {
        if (visionModel == null) {
            return "[视觉模型不可用，无法描述图片]";
        }
        
        try {
            // 验证URL
            new URL(imageUrl);
            
            String prompt = IMAGE_DESCRIPTION_PROMPT + "\n[图片URL: " + imageUrl + "]";
            String description = visionModel.chat(prompt);
            
            return description != null ? description.trim() : "[图片描述生成失败]";
            
        } catch (Exception e) {
            log.error("描述网络图片失败: {}", imageUrl, e);
            return "[图片URL无效或获取失败: " + e.getMessage() + "]";
        }
    }
    
    @Override
    public String describeMermaidDiagram(String code) {
        if (code == null || code.isEmpty()) {
            return "[空Mermaid图表]";
        }
        
        // 简单分析Mermaid图表结构
        String diagramType = detectMermaidType(code);
        StringBuilder description = new StringBuilder();
        description.append("[Mermaid ").append(diagramType).append("图表]");
        
        // 提取关键节点和关系
        List<String> nodes = extractMermaidNodes(code);
        List<String> edges = extractMermaidEdges(code);
        
        if (!nodes.isEmpty()) {
            description.append(" 节点包括: ").append(String.join(", ", nodes));
        }
        if (!edges.isEmpty()) {
            description.append(" 关系: ").append(String.join("; ", edges));
        }
        
        // 如果有视觉模型，可以进一步分析
        if (visionModel != null) {
            try {
                String prompt = DIAGRAM_DESCRIPTION_PROMPT + "\n```mermaid\n" + code + "\n```";
                String llmDescription = visionModel.chat(prompt);
                if (llmDescription != null && !llmDescription.isEmpty()) {
                    description.append(" LLM分析: ").append(llmDescription.trim());
                }
            } catch (Exception e) {
                log.debug("Mermaid图表LLM描述失败: {}", e.getMessage());
            }
        }
        
        return description.toString();
    }
    
    @Override
    public String describePlantUmlDiagram(String code) {
        if (code == null || code.isEmpty()) {
            return "[空PlantUML图表]";
        }
        
        // 检测PlantUML图表类型
        String diagramType = detectPlantUmlType(code);
        StringBuilder description = new StringBuilder();
        description.append("[PlantUML ").append(diagramType).append("图表]");
        
        // 提取关键组件
        List<String> components = extractPlantUmlComponents(code);
        if (!components.isEmpty()) {
            description.append(" 组件包括: ").append(String.join(", ", components));
        }
        
        // 如果有视觉模型，可以进一步分析
        if (visionModel != null) {
            try {
                String prompt = DIAGRAM_DESCRIPTION_PROMPT + "\n```plantuml\n" + code + "\n```";
                String llmDescription = visionModel.chat(prompt);
                if (llmDescription != null && !llmDescription.isEmpty()) {
                    description.append(" LLM分析: ").append(llmDescription.trim());
                }
            } catch (Exception e) {
                log.debug("PlantUML图表LLM描述失败: {}", e.getMessage());
            }
        }
        
        return description.toString();
    }
    
    @Override
    public String detectDiagramLanguage(String code) {
        if (code == null) {
            return null;
        }
        
        String lowerCode = code.toLowerCase();
        
        // PlantUML
        if (code.contains(PLANTUML_START) || code.contains("@startuml")) {
            return "plantuml";
        }
        
        // Mermaid
        if (lowerCode.contains("graph") || 
            lowerCode.contains("pie ") ||
            lowerCode.contains("gantt") ||
            lowerCode.contains("sequence diagram") ||
            lowerCode.contains("class diagram") ||
            lowerCode.contains("state diagram") ||
            lowerCode.contains("er diagram") ||
            lowerCode.contains("flowchart")) {
            return "mermaid";
        }
        
        return null;
    }
    
    /**
     * 检测Mermaid图表类型
     */
    private String detectMermaidType(String code) {
        if (code == null) return "未知";
        
        String lowerCode = code.toLowerCase();
        if (lowerCode.contains("graph TD") || lowerCode.contains("graph LR") || 
            lowerCode.contains("graph TB") || lowerCode.contains("graph BT")) {
            return "流程图";
        } else if (lowerCode.contains("sequence diagram")) {
            return "时序图";
        } else if (lowerCode.contains("class diagram")) {
            return "类图";
        } else if (lowerCode.contains("state diagram")) {
            return "状态图";
        } else if (lowerCode.contains("er diagram")) {
            return "ER图";
        } else if (lowerCode.contains("gantt")) {
            return "甘特图";
        } else if (lowerCode.contains("pie")) {
            return "饼图";
        } else if (lowerCode.contains("gitGraph")) {
            return "Git图";
        } else if (lowerCode.contains("mindmap")) {
            return "思维导图";
        } else if (lowerCode.contains("timeline")) {
            return "时间线";
        } else if (lowerCode.contains("requirement")) {
            return "需求图";
        }
        
        return "流程图";
    }
    
    /**
     * 检测PlantUML图表类型
     */
    private String detectPlantUmlType(String code) {
        if (code == null) return "未知";
        
        if (code.contains("@startuml") && code.contains("actor")) {
            return "用例图";
        } else if (code.contains("@startuml") && code.contains("participant")) {
            return "时序图";
        } else if (code.contains("@startuml") && code.contains("class ")) {
            return "类图";
        } else if (code.contains("@startuml") && code.contains("interface ")) {
            return "接口图";
        } else if (code.contains("@startuml") && code.contains("component")) {
            return "组件图";
        } else if (code.contains("@startuml") && code.contains("state ")) {
            return "状态图";
        } else if (code.contains("@startuml") && code.contains("usecase")) {
            return "用例图";
        } else if (code.contains("@startuml") && code.contains("object")) {
            return "对象图";
        } else if (code.contains("@startuml") && code.contains("deployment")) {
            return "部署图";
        } else if (code.contains("@startuml") && code.contains("activity")) {
            return "活动图";
        }
        
        return "UML图";
    }
    
    /**
     * 提取Mermaid节点
     */
    private List<String> extractMermaidNodes(String code) {
        List<String> nodes = new ArrayList<>();
        if (code == null) return nodes;
        
        // 匹配 A[文本] 或 A(文本) 等节点定义
        Pattern nodePattern = Pattern.compile("([A-Za-z0-9_]+)\\s*(?:\\[[^\\]]+\\]|\\([^)]+\\)|\\{[^}]+\\})?");
        Matcher matcher = nodePattern.matcher(code);
        
        while (matcher.find()) {
            String nodeId = matcher.group(1);
            if (!nodeId.equalsIgnoreCase("graph") && !nodeId.matches("\\d+")) {
                nodes.add(nodeId);
            }
        }
        
        return nodes;
    }
    
    /**
     * 提取Mermaid边
     */
    private List<String> extractMermaidEdges(String code) {
        List<String> edges = new ArrayList<>();
        if (code == null) return edges;
        
        // 匹配 A --> B 格式
        Pattern edgePattern = Pattern.compile("([A-Za-z0-9_]+)\\s*(?:-->|-->\\|.*\\||-\\.->|===>|---)\\s*([A-Za-z0-9_]+)");
        Matcher matcher = edgePattern.matcher(code);
        
        while (matcher.find()) {
            String from = matcher.group(1);
            String to = matcher.group(2);
            edges.add(from + " → " + to);
        }
        
        return edges;
    }
    
    /**
     * 提取PlantUML组件
     */
    private List<String> extractPlantUmlComponents(String code) {
        List<String> components = new ArrayList<>();
        if (code == null) return components;
        
        // 匹配 actor, class, interface, component 等定义
        String[] patterns = {
            "actor\\s+([A-Za-z0-9_]+)",
            "class\\s+([A-Za-z0-9_]+)",
            "interface\\s+([A-Za-z0-9_]+)",
            "component\\s+([A-Za-z0-9_]+)",
            "participant\\s+([A-Za-z0-9_]+)",
            "object\\s+([A-Za-z0-9_]+)"
        };
        
        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(code);
            while (m.find()) {
                components.add(m.group(1));
            }
        }
        
        return components;
    }
    
    /**
     * 将Markdown表格转换为可读文本
     */
    private String convertTableToText(String tableContent) {
        if (tableContent == null || tableContent.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        String[] lines = tableContent.split("\\r?\\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 跳过分隔行
            if (line.matches("\\|\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)*\\|?")) {
                continue;
            }
            
            // 解析表格单元格
            String[] cells = line.split("\\|");
            StringBuilder rowText = new StringBuilder();
            
            for (int j = 0; j < cells.length; j++) {
                String cell = cells[j].trim();
                // 跳过空的单元格
                if (j == 0 && cell.isEmpty()) {
                    continue;
                }
                // 跳过末尾的空单元格
                if (j == cells.length - 1 && cell.isEmpty()) {
                    continue;
                }
                
                if (rowText.length() > 0) {
                    rowText.append(" | ");
                }
                rowText.append(cell);
            }
            
            if (rowText.length() > 0) {
                // 第一行作为表头加粗标记
                if (i == 0 || !lines[0].trim().contains("---")) {
                    result.append(rowText);
                } else {
                    // 表头后面有分隔线的情况
                    result.append(rowText);
                }
                result.append("\n");
            }
        }
        
        return result.toString().trim();
    }
    
}
