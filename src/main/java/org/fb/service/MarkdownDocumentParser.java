package org.fb.service;

import org.fb.bean.MarkdownBlock;

import java.nio.file.Path;
import java.util.List;

/**
 * Markdown文档解析服务接口
 * 用于解析Markdown文档，提取各类内容块并处理图片、图表等
 */
public interface MarkdownDocumentParser {
    
    /**
     * 解析Markdown文档
     *
     * @param mdContent Markdown文档内容
     * @param baseDir 文档所在目录 (用于解析相对路径图片)
     * @return 解析后的MarkdownBlock列表
     */
    List<MarkdownBlock> parse(String mdContent, Path baseDir);
    
    /**
     * 解析Markdown文档 (仅解析结构，不处理图片)
     *
     * @param mdContent Markdown文档内容
     * @return 解析后的MarkdownBlock列表
     */
    List<MarkdownBlock> parseBlocks(String mdContent);
    
    /**
     * 处理图片块 - 调用视觉模型生成描述
     *
     * @param block 图片块
     * @param baseDir 文档所在目录
     */
    void processImageBlock(MarkdownBlock block, Path baseDir);
    
    /**
     * 处理图表代码块 - 生成图表描述
     *
     * @param block 图表代码块
     */
    void processDiagramBlock(MarkdownBlock block);
    
    /**
     * 处理表格块 - 转换为可读文本
     *
     * @param block 表格块
     */
    void processTableBlock(MarkdownBlock block);
    
    /**
     * 描述本地图片 - 调用视觉模型
     *
     * @param imagePath 本地图片路径
     * @return 图片描述
     */
    String describeLocalImage(Path imagePath);
    
    /**
     * 描述网络图片
     *
     * @param imageUrl 网络图片URL
     * @return 图片描述
     */
    String describeRemoteImage(String imageUrl);
    
    /**
     * 描述Mermaid图表
     *
     * @param code Mermaid代码
     * @return 图表描述
     */
    String describeMermaidDiagram(String code);
    
    /**
     * 描述PlantUML图表
     *
     * @param code PlantUML代码
     * @return 图表描述
     */
    String describePlantUmlDiagram(String code);
    
    /**
     * 检测图表类型
     *
     * @param code 代码内容
     * @return 图表类型 (mermaid, plantuml, 或 null)
     */
    String detectDiagramLanguage(String code);
    
    /**
     * 批量处理所有块
     *
     * @param blocks MarkdownBlock列表
     * @param baseDir 文档所在目录
     */
    void processBlocks(List<MarkdownBlock> blocks, Path baseDir);
}
