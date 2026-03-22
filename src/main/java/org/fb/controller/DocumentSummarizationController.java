package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fb.bean.Result;
import org.fb.bean.SummaryChunk;
import org.fb.bean.SummarizationResult;
import org.fb.bean.VectorStoreResult;
import org.fb.service.DocumentSummarizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 文档概要摘要控制器
 */
@RestController
@RequestMapping("/api/summarize")
@Tag(name = "文档概要摘要")
public class DocumentSummarizationController {
    private static final Logger log = LoggerFactory.getLogger(DocumentSummarizationController.class);

    private static final int MAX_FILES = 5;

    @Autowired
    private DocumentSummarizationService documentSummarizationService;

    @Autowired
    private ExecutorService executorService;

    /**
     * 上传单个文档并生成概要摘要（包含向量入库结果）
     *
     * @param file 上传的文档文件（支持PDF、MD、Word等）
     * @return 切分摘要结果和向量入库状态
     */
    @PostMapping("/summarize")
    @Operation(summary = "文档概要摘要", description = "上传文档，生成语义切分和摘要，同时保存到向量数据库")
    public Result<Map<String, Object>> summarizeDocument(@RequestParam("file") MultipartFile file) {
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
            
            // 执行摘要（包含向量入库）
            SummarizationResult summarizationResult = documentSummarizationService.summarizeDocument(file, true);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("chunks", summarizationResult.getChunks());
            response.put("chunkCount", summarizationResult.getChunkCount());
            response.put("processingTimeMs", summarizationResult.getProcessingTimeMs());
            response.put("vectorStore", summarizationResult.getVectorStoreResult());
            
            String message = "概要摘要生成成功";
            if (summarizationResult.getVectorStoreResult() != null) {
                message += "，" + summarizationResult.getVectorStoreResult().getStatusDescription();
            }
            
            log.info("概要摘要生成完成，共 {} 个切片，处理耗时 {}ms", 
                    summarizationResult.getChunkCount(), summarizationResult.getProcessingTimeMs());
            return Result.success(message, response);
            
        } catch (Exception e) {
            log.error("概要摘要处理失败", e);
            return Result.error("概要摘要处理失败: " + e.getMessage());
        }
    }

    /**
     * 上传多个文档（最多5个）并生成概要摘要（包含向量入库结果）
     *
     * @param files 上传的文档文件数组（支持PDF、MD、Word等）
     * @return 批量处理结果
     */
    @PostMapping("/summarizeMultiple")
    @Operation(summary = "批量文档概要摘要", description = "上传多个文档（最多5个），并行生成语义切分和摘要，同时保存到向量数据库")
    public Result<Map<String, Object>> summarizeMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        log.info("收到批量概要摘要请求，共 {} 个文件", files != null ? files.length : 0);
        
        try {
            // 校验文件数组
            if (files == null || files.length == 0) {
                return Result.error("请选择要上传的文件");
            }
            
            // 校验文件数量
            if (files.length > MAX_FILES) {
                return Result.error("最多支持同时处理 " + MAX_FILES + " 个文件");
            }
            
            // 过滤空文件并校验
            List<String> invalidFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String filename = file.getOriginalFilename();
                if (filename != null) {
                    String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
                    if (!isValidFileType(extension)) {
                        invalidFiles.add(filename);
                    }
                }
            }
            
            if (!invalidFiles.isEmpty()) {
                return Result.error("以下文件类型不支持: " + String.join(", ", invalidFiles) + "，仅支持: PDF, MD, TXT, DOC, DOCX, PPTX");
            }
            
            // 并行处理多个文件
            log.info("开始并行处理 {} 个文件", files.length);
            
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                
                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("fileName", file.getOriginalFilename());
                    
                    try {
                        SummarizationResult summarizationResult = documentSummarizationService.summarizeDocument(file, true);
                        List<SummaryChunk> chunks = summarizationResult.getChunks();
                        VectorStoreResult vectorStore = summarizationResult.getVectorStoreResult();
                        
                        result.put("success", true);
                        result.put("chunks", chunks);
                        result.put("chunkCount", chunks.size());
                        result.put("processingTimeMs", summarizationResult.getProcessingTimeMs());
                        result.put("vectorStore", vectorStore);
                        
                        log.info("文件 {} 处理完成，共 {} 个切片", file.getOriginalFilename(), chunks.size());
                    } catch (Exception e) {
                        result.put("success", false);
                        result.put("error", e.getMessage());
                        log.error("文件 {} 处理失败: {}", file.getOriginalFilename(), e.getMessage());
                    }
                    
                    return result;
                }, executorService);
                
                futures.add(future);
            }
            
            // 收集结果
            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int totalChunks = 0;
            long totalProcessingTime = 0;
            List<SummaryChunk> allChunks = new ArrayList<>();
            List<VectorStoreResult> allVectorStores = new ArrayList<>();
            
            for (CompletableFuture<Map<String, Object>> future : futures) {
                try {
                    Map<String, Object> result = future.get();
                    results.add(result);
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        successCount++;
                        totalChunks += (Integer) result.get("chunkCount");
                        totalProcessingTime += ((Number) result.get("processingTimeMs")).longValue();
                        @SuppressWarnings("unchecked")
                        List<SummaryChunk> chunks = (List<SummaryChunk>) result.get("chunks");
                        allChunks.addAll(chunks);
                        
                        if (result.get("vectorStore") != null) {
                            allVectorStores.add((VectorStoreResult) result.get("vectorStore"));
                        }
                    }
                } catch (Exception e) {
                    log.error("获取处理结果失败", e);
                }
            }
            
            // 按文件分组排序
            final int[] chunkIndex = {1};
            allChunks.sort((a, b) -> {
                int titleCompare = a.getArticleTitle().compareTo(b.getArticleTitle());
                if (titleCompare != 0) return titleCompare;
                return Integer.compare(a.getChunkIndex(), b.getChunkIndex());
            });
            for (SummaryChunk chunk : allChunks) {
                chunk.setChunkIndex(chunkIndex[0]++);
            }
            
            // 计算向量入库汇总
            VectorStoreResult totalVectorStore = null;
            if (!allVectorStores.isEmpty()) {
                int totalStored = allVectorStores.stream()
                        .filter(v -> v.isSuccess())
                        .mapToInt(VectorStoreResult::getChunkCount)
                        .sum();
                long totalStoredTime = allVectorStores.stream()
                        .filter(v -> v.isSuccess())
                        .mapToLong(VectorStoreResult::getStoredTimeMs)
                        .sum();
                totalVectorStore = VectorStoreResult.success(
                        allVectorStores.get(0).getCollectionName(),
                        totalStored,
                        totalStoredTime,
                        null
                );
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("totalFiles", files.length);
            response.put("successCount", successCount);
            response.put("totalChunks", totalChunks);
            response.put("totalProcessingTimeMs", totalProcessingTime);
            response.put("allChunks", allChunks);
            response.put("vectorStoreSummary", totalVectorStore);
            
            log.info("批量概要摘要处理完成，成功 {} / {} 个文件，共 {} 个切片，总耗时 {}ms", 
                    successCount, files.length, totalChunks, totalProcessingTime);
            return Result.success("批量处理完成，成功 " + successCount + " / " + files.length + " 个文件", response);
            
        } catch (Exception e) {
            log.error("批量概要摘要处理失败", e);
            return Result.error("批量处理失败: " + e.getMessage());
        }
    }

    /**
     * 对文本内容进行概要摘要（包含向量入库结果）
     *
     * @param content 文本内容
     * @param title   文章标题（可选）
     * @return 切分摘要结果和向量入库状态
     */
    @PostMapping("/summarizeText")
    @Operation(summary = "文本概要摘要", description = "对文本内容进行概要摘要处理，同时保存到向量数据库")
    public Result<Map<String, Object>> summarizeText(
            @RequestParam("content") String content,
            @RequestParam(value = "title", required = false, defaultValue = "未命名文档") String title) {
        
        log.info("收到文本概要摘要请求，标题: {}", title);
        
        try {
            if (content == null || content.trim().isEmpty()) {
                return Result.error("文本内容不能为空");
            }
            
            SummarizationResult summarizationResult = documentSummarizationService.summarizeText(content, title, true);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("chunks", summarizationResult.getChunks());
            response.put("chunkCount", summarizationResult.getChunkCount());
            response.put("processingTimeMs", summarizationResult.getProcessingTimeMs());
            response.put("vectorStore", summarizationResult.getVectorStoreResult());
            
            String message = "概要摘要生成成功";
            if (summarizationResult.getVectorStoreResult() != null) {
                message += "，" + summarizationResult.getVectorStoreResult().getStatusDescription();
            }
            
            log.info("文本概要摘要生成完成，共 {} 个切片，处理耗时 {}ms", 
                    summarizationResult.getChunkCount(), summarizationResult.getProcessingTimeMs());
            return Result.success(message, response);
            
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
