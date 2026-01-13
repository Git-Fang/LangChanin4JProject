package org.fb.controller;

import dev.langchain4j.data.segment.TextSegment;
import org.fb.util.BatchPathProvider;
import org.fb.service.impl.DocumentImpl;
import org.fb.service.assistant.TermExtractionAgent;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "个人数据管理")
@RestController
@RequestMapping("/xiaozhi/personal")
public class PersonalDataController {
    private static final Logger log = LoggerFactory.getLogger(PersonalDataController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private BatchPathProvider batchPathProvider;

    @Autowired
    private DocumentImpl documentImpl;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @PostMapping("/upload")
    @Operation(summary = "上传个人相关文件")
    public Map<String, Object> uploadResume(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            MultipartFile[] files = new MultipartFile[]{file};
            Integer count = documentService.saveAndEmbedding(files);
            
            result.put("success", true);
            result.put("message", "文件上传成功，已向量化存储到qdrant数据库");
            result.put("count", count);
            log.info("个人简历文件上传成功: {}", file.getOriginalFilename());
        } catch (Exception e) {
            log.error("上传个人简历文件失败", e);
            result.put("success", false);
            result.put("message", "文件上传失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/upload/batch")
    @Operation(summary = "批量上传个人相关文件")
    public Map<String, Object> uploadResumeBatch(@RequestParam("files") MultipartFile[] files,
                                                @RequestParam(value = "operation", defaultValue = "VECTORIZE") String operation) {
        if (files != null && files.length > 5) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "最多支持上传 5 个文件");
            return result;
        }
        Map<String, Object> result = new HashMap<>();
        java.util.Map<String, Object> fileResults = new LinkedHashMap<>();
        try {
            if (operation == null || "VECTORIZE".equalsIgnoreCase(operation)) {
                Integer count = 0;
                for (MultipartFile file : files) {
                    try {
                        MultipartFile[] singleFile = new MultipartFile[]{file};
                        documentService.saveAndEmbedding(singleFile);
                        count++;
                        fileResults.put(file.getOriginalFilename(), java.util.Map.of("success", true, "message", "文件已向量化存储"));
                    } catch (Exception e) {
                        log.error("文件向量化失败: {}", file.getOriginalFilename(), e);
                        fileResults.put(file.getOriginalFilename(), java.util.Map.of("success", false, "message", "处理失败: " + e.getMessage()));
                    }
                }
                result.put("success", true);
                result.put("message", "批量上传成功，已向量化存储到qdrant数据库");
                result.put("results", fileResults);
                result.put("count", count);
                log.info("批量上传个人简历文件成功，共{}个文件", count);
            } else if ("TERMS".equalsIgnoreCase(operation)) {
                List<String> paths = batchPathProvider.saveFilesToLocalAndReturnPaths(files);
                java.util.Map<String, String> termsMap = new LinkedHashMap<>();
                for (String path : paths) {
                    try {
                        List<TextSegment> segments = documentImpl.parseAndEmbedding(path);
                        String fileText = segments.stream().map(TextSegment::text).collect(Collectors.joining("\n"));
                        if (fileText != null && !fileText.isEmpty() && !fileText.contains("未检测到文字")) {
                            String terms = termExtractionAgent.chatWithTermTool(fileText);
                            File f = new File(path);
                            termsMap.put(f.getName(), terms);
                            fileResults.put(f.getName(), java.util.Map.of("success", true, "terms", terms, "message", "术语解析成功"));
                        } else {
                            File f = new File(path);
                            String message = "[图片OCR] 图片中未检测到文字内容，无需提取术语";
                            termsMap.put(f.getName(), message);
                            fileResults.put(f.getName(), java.util.Map.of("success", true, "terms", message, "message", "图片中未检测到文字内容"));
                        }
                        deleteTempFile(path);
                    } catch (Exception e) {
                        log.error("处理文件失败: {}", path, e);
                        File f = new File(path);
                        termsMap.put(f.getName(), "处理失败: " + e.getMessage());
                        fileResults.put(f.getName(), java.util.Map.of("success", false, "message", "处理失败: " + e.getMessage()));
                    }
                }
                result.put("success", true);
                result.put("message", "批量术语解析完成");
                result.put("results", fileResults);
                result.put("terms", termsMap);
                result.put("count", termsMap.size());
            }
        } catch (Exception e) {
            log.error("Batch batch upload failed", e);
            result.put("success", false);
            result.put("message", "Batch upload failed: " + e.getMessage());
        }
        return result;
    }

    private void deleteTempFile(String path) {
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
        } catch (Exception e) {
            log.warn("删除临时文件失败: {}", path);
        }
    }

    @PostMapping("/text")
    @Operation(summary = "添加个人简历文本信息")
    public Map<String, Object> addResumeText(@RequestBody String text) {
        Map<String, Object> result = new HashMap<>();
        try {
            documentService.addText(text);
            
            result.put("success", true);
            result.put("message", "文本信息添加成功，已向量化存储到qdrant数据库");
            log.info("个人简历文本信息添加成功");
        } catch (Exception e) {
            log.error("添加个人简历文本信息失败", e);
            result.put("success", false);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }
}
