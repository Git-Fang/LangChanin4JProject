package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.Result;
import org.fb.bean.SummaryChunk;
import org.fb.service.DocumentSummarizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档概要摘要控制器
 */
@RestController
@RequestMapping("/ragTranslation/doc")
@Tag(name = "文档概要摘要")
public class DocumentSummarizationController {
    private static final Logger log = LoggerFactory.getLogger(DocumentSummarizationController.class);

    @Autowired
    private DocumentSummarizationService documentSummarizationService;

    /**
     * 上传文档并生成概要摘要
     *
     * @param file 上传的文档文件（支持PDF、MD、Word等）
     * @return 切分摘要结果
     */
    @PostMapping("/summarize")
    @Operation(summary = "文档概要摘要", description = "上传文档，生成语义切分和摘要")
    public Result<List<SummaryChunk>> summarizeDocument(@RequestParam("file") MultipartFile file) {
        log.info("收到概要摘要请求: {}", file.getOriginalFilename());
        
        try {
            // 校验文件
            if (file == null || file.isEmpty()) {
                return Result.error("请选择要上传的文件");
            }
            
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                return Result.error("文件名无效");
            }
            
            // 校验文件类型
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (!isValidFileType(extension)) {
                return Result.error("不支持的文件类型，仅支持: PDF, MD, TXT, DOC, DOCX, PPTX");
            }
            
            // 执行摘要
            List<SummaryChunk> results = documentSummarizationService.summarizeDocument(file);
            
            log.info("概要摘要生成完成，共 {} 个切片", results.size());
            return Result.success("概要摘要生成成功", results);
            
        } catch (Exception e) {
            log.error("概要摘要处理失败", e);
            return Result.error("概要摘要处理失败: " + e.getMessage());
        }
    }

    /**
     * 对文本内容进行概要摘要
     *
     * @param content 文本内容
     * @param title   文章标题（可选）
     * @return 切分摘要结果
     */
    @PostMapping("/summarizeText")
    @Operation(summary = "文本概要摘要", description = "对文本内容进行概要摘要处理")
    public Result<List<SummaryChunk>> summarizeText(
            @RequestParam("content") String content,
            @RequestParam(value = "title", required = false, defaultValue = "未命名文档") String title) {
        
        log.info("收到文本概要摘要请求，标题: {}", title);
        
        try {
            if (content == null || content.trim().isEmpty()) {
                return Result.error("文本内容不能为空");
            }
            
            List<SummaryChunk> results = documentSummarizationService.summarizeText(content, title);
            
            log.info("文本概要摘要生成完成，共 {} 个切片", results.size());
            return Result.success("概要摘要生成成功", results);
            
        } catch (Exception e) {
            log.error("概要摘要处理失败", e);
            return Result.error("概要摘要处理失败: " + e.getMessage());
        }
    }

    /**
     * 校验文件类型
     */
    private boolean isValidFileType(String extension) {
        return "pdf".equals(extension) ||
                "md".equals(extension) ||
                "txt".equals(extension) ||
                "doc".equals(extension) ||
                "docx".equals(extension) ||
                "pptx".equals(extension);
    }
}
