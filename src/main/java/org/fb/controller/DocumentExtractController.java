package org.fb.controller;

import dev.langchain4j.data.segment.TextSegment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "文档提取", description = "文档内容提取API")
@RestController
@RequestMapping("/xiaozhi/chat")
public class DocumentExtractController {
    private static final Logger log = LoggerFactory.getLogger(DocumentExtractController.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES = 3;

    @Autowired
    private DocumentService documentService;

    @PostMapping("/extract")
    @Operation(summary = "提取上传文档内容", description = "解析上传的文件并返回提取的文本内容")
    public Map<String, Object> extractDocument(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> result = new HashMap<>();
        List<String> extractedTexts = new ArrayList<>();
        List<Map<String, Object>> fileResults = new ArrayList<>();

        try {
            if (files == null || files.length == 0) {
                result.put("success", false);
                result.put("message", "请选择要上传的文件");
                return result;
            }

            if (files.length > MAX_FILES) {
                result.put("success", false);
                result.put("message", "最多支持上传 " + MAX_FILES + " 个文件");
                return result;
            }

            List<MultipartFile> validFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    log.warn("跳过空文件: {}", file.getOriginalFilename());
                    continue;
                }

                if (file.getSize() > MAX_FILE_SIZE) {
                    log.warn("跳过超出大小限制的文件: {} (大小: {}MB)",
                            file.getOriginalFilename(), file.getSize() / (1024 * 1024));
                    continue;
                }

                String fileName = file.getOriginalFilename();
                if (fileName == null) {
                    continue;
                }

                String extension = getFileExtension(fileName).toLowerCase();
                if (!isValidFileType(extension)) {
                    log.warn("跳过不支持的文件类型: {}", fileName);
                    continue;
                }

                validFiles.add(file);
            }

            if (validFiles.isEmpty()) {
                result.put("success", false);
                result.put("message", "没有有效的文件，请检查文件类型和大小");
                return result;
            }

            for (MultipartFile file : validFiles) {
                Map<String, Object> fileResult = new HashMap<>();
                String fileName = file.getOriginalFilename();
                fileResult.put("fileName", fileName);

                try {
                    String tempFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                    java.nio.file.Path tempFilePath = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), tempFileName);
                    file.transferTo(tempFilePath.toFile());

                    List<TextSegment> segments = documentService.parseAndEmbedding(tempFilePath.toString());

                    java.nio.file.Files.deleteIfExists(tempFilePath);

                    StringBuilder extractedContent = new StringBuilder();
                    for (TextSegment segment : segments) {
                        if (extractedContent.length() > 0) {
                            extractedContent.append("\n\n");
                        }
                        extractedContent.append(segment.text());
                    }

                    String content = extractedContent.toString();
                    if (content.isEmpty()) {
                        content = "文件解析完成，但未提取到文本内容";
                    }

                    extractedTexts.add(content);
                    fileResult.put("success", true);
                    fileResult.put("message", "提取成功");
                    fileResult.put("contentLength", content.length());

                    log.info("文件提取完成: {}, 提取文本长度: {}", fileName, content.length());
                } catch (Exception e) {
                    log.error("文件提取失败: {}", fileName, e);
                    fileResult.put("success", false);
                    fileResult.put("message", "提取失败: " + e.getMessage());
                }

                fileResults.add(fileResult);
            }

            result.put("success", true);
            result.put("message", "文件提取完成");
            result.put("extractedTexts", extractedTexts);
            result.put("fileResults", fileResults);
            result.put("totalFiles", validFiles.size());

        } catch (Exception e) {
            log.error("文档提取过程发生异常", e);
            result.put("success", false);
            result.put("message", "文档提取失败: " + e.getMessage());
        }

        return result;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(dot + 1).toLowerCase();
        }
        return "";
    }

    private boolean isValidFileType(String extension) {
        String[] validTypes = {"jpg", "jpeg", "png", "md", "doc", "docx", "pptx", "xlsx", "txt", "pdf"};
        for (String type : validTypes) {
            if (type.equals(extension)) {
                return true;
            }
        }
        return false;
    }
}
