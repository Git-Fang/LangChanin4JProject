package org.fb.controller;

import dev.langchain4j.data.segment.TextSegment;
import org.fb.util.BatchPathProvider;
import org.fb.service.impl.DocumentImpl;
import org.fb.service.assistant.TermExtractionAgent;
import org.fb.tools.QdrantOperationTools;
import mapper.FileOperationMapper;
import org.fb.bean.FileOperation;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import java.util.ArrayList;

@Tag(name = "个人数据管理")
@RestController
@RequestMapping("/xiaozhi/personal")
public class PersonalDataController {
    private static final Logger log = LoggerFactory.getLogger(PersonalDataController.class);
    
    private static final int MAX_PARAGRAPH_LENGTH = 800;
    private static final int THREAD_POOL_SIZE = 5;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private BatchPathProvider batchPathProvider;

    @Autowired
    private DocumentImpl documentImpl;

    @Autowired
    private TermExtractionAgent termExtractionAgent;
    
    @Autowired
    private QdrantOperationTools qdrantOperationTools;
    
    @Autowired
    private FileOperationMapper fileOperationMapper;

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
                List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
                
                for (MultipartFile file : files) {
                    String fileName = file.getOriginalFilename();
                    String fileType = getFileExtension(fileName);
                    Long operationId = recordFileOperation(fileName, fileType, "VECTORIZE", "PROCESSING");
                    
                    CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                        Map<String, Object> fileResult = new HashMap<>();
                        try {
                            MultipartFile[] singleFile = new MultipartFile[]{file};
                            documentService.saveAndEmbedding(singleFile);
                            fileResult.put(fileName, java.util.Map.of("success", true, "message", "文件已向量化存储"));
                            updateFileOperationStatus(operationId, "SUCCESS");
                            return fileResult;
                        } catch (Exception e) {
                            log.error("文件向量化失败: {}", fileName, e);
                            updateFileOperationStatus(operationId, "FAILED");
                            Map<String, Object> errorResult = new HashMap<>();
                            errorResult.put(fileName, java.util.Map.of("success", false, "message", "处理失败: " + e.getMessage()));
                            return errorResult;
                        }
                    }, executorService);
                    futures.add(future);
                }
                for (CompletableFuture<Map<String, Object>> future : futures) {
                    try {
                        Map<String, Object> fileResult = future.get();
                        fileResults.putAll(fileResult);
                        count++;
                    } catch (Exception e) {
                        log.error("获取文件处理结果失败", e);
                    }
                }
                
                result.put("success", true);
                result.put("message", "批量上传成功，已向量化存储到qdrant数据库");
                result.put("results", fileResults);
                result.put("count", count);
                log.info("批量上传个人简历文件成功，共{}个文件", count);
            } else if ("TERMS".equalsIgnoreCase(operation)) {
                java.util.Map<String, String> termsMap = new LinkedHashMap<>();
                List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
                
                List<String> paths = batchPathProvider.saveFilesToLocalAndReturnPaths(files);
                for (String path : paths) {
                    File file = new File(path);
                    String fileName = file.getName();
                    String originalName = getOriginalFileName(fileName);
                    String fileType = getFileExtension(originalName);
                    Long operationId = recordFileOperation(originalName, fileType, "TERMS", "PROCESSING");
                    
                    CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                        Map<String, Object> fileResult = processTermExtraction(path, operationId);
                        return fileResult;
                    }, executorService);
                    futures.add(future);
                }
                for (CompletableFuture<Map<String, Object>> future : futures) {
                    try {
                        Map<String, Object> fileResult = future.get();
                        String resultFileName = (String) fileResult.get("fileName");
                        Object terms = fileResult.get("terms");
                        if (terms != null) {
                            termsMap.put(resultFileName, terms.toString());
                        }
                        fileResults.put(resultFileName, fileResult);
                    } catch (Exception e) {
                        log.error("获取术语提取结果失败", e);
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
    
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(dot + 1).toLowerCase();
        }
        return "unknown";
    }
    
    private String getOriginalFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        int underscoreIndex = fileName.indexOf('_');
        if (underscoreIndex > 0) {
            String ext = getFileExtension(fileName);
            String baseName = fileName.substring(0, underscoreIndex);
            int uuidStart = fileName.lastIndexOf('_', underscoreIndex - 1);
            if (uuidStart > 0) {
                baseName = fileName.substring(0, uuidStart);
            }
            return baseName + "." + ext;
        }
        return fileName;
    }
    
    private Long recordFileOperation(String fileName, String fileType, String operationType, String status) {
        try {
            FileOperation fileOperation = new FileOperation(fileName, fileType, operationType, status);
            fileOperationMapper.insert(fileOperation);
            log.info("记录文件操作: fileName={}, operationType={}, status={}", fileName, operationType, status);
            return fileOperation.getId();
        } catch (Exception e) {
            log.error("记录文件操作失败: {}", fileName, e);
            return null;
        }
    }
    
    private void updateFileOperationStatus(Long operationId, String status) {
        if (operationId == null) return;
        try {
            FileOperation fileOperation = fileOperationMapper.selectById(operationId);
            if (fileOperation != null) {
                fileOperation.setStatus(status);
                fileOperationMapper.updateById(fileOperation);
                log.info("更新文件操作状态: id={}, status={}", operationId, status);
            }
        } catch (Exception e) {
            log.error("更新文件操作状态失败: id={}", operationId, e);
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


    private Map<String, Object> processTermExtraction(String path, Long operationId) {
        Map<String, Object> result = new HashMap<>();
        File f = new File(path);
        String fileName = f.getName();
        String originalName = getOriginalFileName(fileName);
        result.put("fileName", originalName);

        try {
            List<TextSegment> segments = documentImpl.parseAndEmbedding(path);
            String fileText = segments.stream().map(TextSegment::text).collect(Collectors.joining("\n"));

            if (fileText != null && !fileText.isEmpty() && !fileText.contains("未检测到文字")) {
                // 文档切分成块
                List<String> textChunks = splitTextByParagraph(fileText);

                StringBuilder allTerms = new StringBuilder();
                for (int i = 0; i < textChunks.size(); i++) {
                    String chunk = textChunks.get(i);
                    log.info("处理第 {} / {} 个文本块，文本长度: {}", i + 1, textChunks.size(), chunk.length());

                    String terms = termExtractionAgent.chatWithTermTool(chunk);
                    if (terms != null && !terms.isEmpty()) {
                        allTerms.append(terms).append("\n");
                        qdrantOperationTools.embeddingTermAndSave(terms);
                    }
                }

                String termsResult = allTerms.toString().trim();
                if (termsResult.isEmpty()) {
                    termsResult = "未提取到术语";
                }

                result.put("terms", termsResult);
                result.put("success", true);
                result.put("message", "术语解析成功");
                updateFileOperationStatus(operationId, "SUCCESS");
            } else {
                String message = "[图片OCR] 图片中未检测到文字内容，无需提取术语";
                result.put("terms", message);
                result.put("success", true);
                result.put("message", "图片中未检测到文字内容");
                updateFileOperationStatus(operationId, "SUCCESS");
            }
            deleteTempFile(path);
        } catch (Exception e) {
            log.error("处理文件失败: {}", path, e);
            result.put("terms", "处理失败: " + e.getMessage());
            result.put("success", false);
            result.put("message", "处理失败: " + e.getMessage());
            updateFileOperationStatus(operationId, "FAILED");
        }

        return result;
    }

    private List<String> splitTextByParagraph(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        String[] paragraphs = text.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                continue;
            }

            if (currentChunk.length() == 0) {
                currentChunk.append(paragraph);
            } else if (currentChunk.length() + paragraph.length() + 1 <= MAX_PARAGRAPH_LENGTH) {
                currentChunk.append("\n").append(paragraph);
            } else {
                chunks.add(currentChunk.toString().trim());
                // 某段内容超过最大长度，则进行语义切分处理
                if (paragraph.length() > MAX_PARAGRAPH_LENGTH) {
                    List<String> semanticChunks = splitLongText(paragraph);
                    chunks.addAll(semanticChunks);
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(paragraph);
                }
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        return chunks;
    }

    private List<String> splitLongText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_PARAGRAPH_LENGTH, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }

        return chunks;
    }
}
