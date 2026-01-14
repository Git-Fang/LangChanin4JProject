package org.fb.service.impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import mapper.FileOperationMapper;
import org.fb.bean.FileOperation;
import org.fb.constant.BusinessConstant;
import org.fb.service.DocumentService;
import org.fb.service.assistant.TermExtractionAgent;
import org.fb.tools.QdrantOperationTools;
import org.fb.util.BatchPathProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class DocumentImpl implements DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentImpl.class);

    @Autowired
    private EmbeddingModel embeddedModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private FileOperationMapper fileOperationMapper;

    @Autowired
    private BatchPathProvider batchPathProvider;

    @Autowired
    private TermExtractionAgent termExtractionAgent;

    @Autowired
    private QdrantOperationTools qdrantOperationTools;

    private static final String storagePath = BusinessConstant.TEMP_FILE_PATH;
    private final ExecutorService executorService = Executors.newFixedThreadPool(BusinessConstant.THREAD_POOL_SIZE);


    @Override
    public Integer saveFilesToLocal(MultipartFile[] files) {
        StringBuilder result = new StringBuilder();
        Path storageDir = Paths.get(storagePath);

        try {
            // 创建目录（如果不存在）
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            int successCount = 0;
            int failCount = 0;

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    result.append("跳过空文件\n");
                    continue;
                }

                String originalFileName = file.getOriginalFilename();
                if (originalFileName == null) {
                    result.append("跳过无文件名的文件\n");
                    failCount++;
                    continue;
                }

                // 校验文件类型
                if (!isValidDocumentType(originalFileName)) {
                    result.append("跳过不支持的文件类型: ").append(originalFileName).append("\n");
                    failCount++;
                    continue;
                }

                // 生成唯一文件名
                String fileExtension = getFileExtension(originalFileName);
                String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;
                Path filePath = storageDir.resolve(uniqueFileName);

                // 保存文件
                file.transferTo(filePath.toFile());
                log.info("文件上传成功:原文件{} 保存至{}" ,originalFileName, filePath);
                successCount++;
            }

            log.warn("上传完成 - 成功: " + successCount + ", 失败: " + failCount);
            return successCount;
        } catch (IOException e) {
            log.error("批量文件上传失败", e);
            throw new RuntimeException("批量文件上传失败", e);
        }
    }

    @Override
    public Integer saveAndEmbedding(MultipartFile[] files) {

        int successCount = 0;

        for (MultipartFile file : files) {
            String path = saveFileToLocal(file);
            
            if (path == null || path.isEmpty()) {
                log.warn("文件保存失败，跳过向量化: {}", file.getOriginalFilename());
                continue;
            }

            parseAndEmbedding(path);
            log.info("{}向量化完成",file.getOriginalFilename());
            successCount++;

            // 向量完成后，删除path对应文件
            deleteFile(path);
        }
        return successCount;
    }

    @Override
    public void addText(String document) {
        // 创建文本段
        TextSegment segment1 = TextSegment.from(document);
        segment1.metadata().put("author", "fb");

        // 向量化
        List<TextSegment> segments = List.of(segment1);
        List<Embedding> embeddings = embeddedModel.embedAll(segments).content();

        // 存储到向量数据库
        embeddingStore.addAll(embeddings, segments);
    }


    @Override
    public List<TextSegment> parseAndEmbedding(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        
        if (extension.matches("jpg|jpeg|png")) {
            return parseImageAndEmbedding(filePath);
        }
        
        Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
        document.metadata().put("author", "fb");

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(800, 80);
        List<TextSegment> segments = splitter.split(document);

        // 对分割后的文档数据进行向量化和存储
//        embeddingAndSave(segments);

        return segments;
    }

    @Override
    public Map<String, Object> processBatchFiles(MultipartFile[] files, String operation) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> fileResults = new LinkedHashMap<>();
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
                            saveAndEmbedding(singleFile);
                            fileResult.put(fileName, Map.of("success", true, "message", "文件已向量化存储"));
                            updateFileOperationStatus(operationId, "SUCCESS");
                            return fileResult;
                        } catch (Exception e) {
                            log.error("文件向量化失败: {}", fileName, e);
                            updateFileOperationStatus(operationId, "FAILED");
                            Map<String, Object> errorResult = new HashMap<>();
                            errorResult.put(fileName, Map.of("success", false, "message", "处理失败: " + e.getMessage()));
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
                Map<String, String> termsMap = new LinkedHashMap<>();
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








    /**
     *   ===============================*/
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

    public List<TextSegment> parseImageAndEmbedding(String imagePath) {
        try {
            String extractedText = performOCR(imagePath);
            
            TextSegment segment = TextSegment.from(extractedText);
            segment.metadata().put("author", "fb");
            segment.metadata().put("source", "ocr");
            segment.metadata().put("originalFile", Paths.get(imagePath).getFileName().toString());

            List<TextSegment> segments = List.of(segment);
            List<Embedding> embeddings = embeddedModel.embedAll(segments).content();

            embeddingStore.addAll(embeddings, segments);
            
            log.info("图片OCR完成，提取文本长度: {}", extractedText.length());
            return segments;
        } catch (Exception e) {
            log.error("图片OCR处理失败: {}", imagePath, e);
            throw new RuntimeException("图片OCR处理失败", e);
        }
    }

    private String performOCR(String imagePath) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(imagePath));
            if (image == null) {
                throw new RuntimeException("无法读取图片文件: " + imagePath);
            }
            
            StringBuilder ocrResult = new StringBuilder();
            
            if (image.getWidth() > 2500 || image.getHeight() > 2500) {
                java.awt.image.BufferedImage scaledImage = new java.awt.image.BufferedImage(
                    image.getWidth() / 2, image.getHeight() / 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = scaledImage.createGraphics();
                g.drawImage(image, 0, 0, scaledImage.getWidth(), scaledImage.getHeight(), null);
                g.dispose();
                image = scaledImage;
            }

            java.awt.image.BufferedImage grayImage = new java.awt.image.BufferedImage(
                image.getWidth(), image.getHeight(), java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
            java.awt.Graphics2D g = grayImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();

            int[] pixels = grayImage.getData().getPixels(0, 0, grayImage.getWidth(), grayImage.getHeight(), new int[grayImage.getWidth() * grayImage.getHeight()]);
            
            int whiteThreshold = 200;
            int blackThreshold = 80;
            
            StringBuilder rowText = new StringBuilder();
            int currentY = 0;
            int rowHeight = 25;
            boolean inTextLine = false;
            StringBuilder currentLine = new StringBuilder();
            int lineStartX = -1;
            int lastTextX = -1;
            
            for (int y = 0; y < grayImage.getHeight(); y += rowHeight) {
                boolean hasText = false;
                StringBuilder line = new StringBuilder();
                int textStart = -1;
                int textEnd = -1;
                
                for (int x = 0; x < grayImage.getWidth(); x++) {
                    boolean isDark = false;
                    for (int dy = 0; dy < rowHeight && y + dy < grayImage.getHeight(); dy++) {
                        int pixel = pixels[(y + dy) * grayImage.getWidth() + x];
                        if (pixel < whiteThreshold) {
                            isDark = true;
                            break;
                        }
                    }
                    
                    if (isDark) {
                        if (!inTextLine) {
                            inTextLine = true;
                            textStart = x;
                        }
                        textEnd = x;
                        hasText = true;
                    }
                }
                
                if (hasText && textStart >= 0) {
                    int charWidth = (textEnd - textStart) / 8;
                    for (int i = 0; i < charWidth && textStart + i <= textEnd; i++) {
                        line.append("█");
                    }
                }
                
                ocrResult.append(line.toString()).append("\n");
            }
            
            String simpleText = ocrResult.toString();
            
            String refinedText = refineOCRText(simpleText, image.getWidth());
            
            return refinedText.isEmpty() ? "图片中未检测到文字内容" : refinedText;
            
        } catch (Exception e) {
            log.error("OCR处理异常: {}", imagePath, e);
            throw new RuntimeException("OCR处理失败", e);
        }
    }
    
    private String refineOCRText(String ocrText, int imageWidth) {
        if (ocrText == null || ocrText.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        String[] lines = ocrText.split("\n");
        
        int avgLineLength = 0;
        int validLineCount = 0;
        
        for (String line : lines) {
            int contentLength = line.replace("█", "").length();
            if (contentLength > 0) {
                avgLineLength += contentLength;
                validLineCount++;
            }
        }
        
        avgLineLength = validLineCount > 0 ? avgLineLength / validLineCount : 0;
        
        for (String line : lines) {
            String content = line.replace("█", "").trim();
            
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() < avgLineLength * 0.3) {
                continue;
            }
            
            result.append(content).append("\n");
        }
        return result.toString().trim();
    }




    // 校验文件类型是否支持
    private boolean isValidDocumentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return extension.equals("pdf") ||
                extension.equals("txt") ||
                extension.equals("md") ||
                extension.equals("doc") ||
                extension.equals("docx") ||
                extension.equals("pptx") ||
                extension.equals("ppt") ||
                extension.equals("jpg") ||
                extension.equals("jpeg") ||
                extension.equals("png");
    }

    // 删除临时文件的辅助方法
    private void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                boolean deleted = Files.deleteIfExists(path);
                if (deleted) {
                    log.info("临时文件删除成功: {}", filePath);
                } else {
                    log.warn("临时文件删除失败，文件不存在: {}", filePath);
                }
            } else {
                log.warn("临时文件不存在，无法删除: {}", filePath);
            }
        } catch (IOException e) {
            log.error("删除临时文件时发生错误: {}", filePath, e);
        }
    }


    /**存储单个文件*/
    private String saveFileToLocal(MultipartFile file) {

        Path storageDir = Paths.get(storagePath);
        try {
            // 创建目录（如果不存在）
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            if (file.isEmpty()) {
               log.error("文件上传失败: 文件为空");
                return "";
            }

            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null) {
               log.error("文件上传失败: 文件无文件名");
                return "";
            }

            // 校验文件类型
            if (!isValidDocumentType(originalFileName)) {
                log.error("文件上传失败: 不支持的文件类型");
                return "";
            }

            // 生成唯一文件名
            String fileExtension = getFileExtension(originalFileName);
            String uniqueFileName = originalFileName+ "_" + UUID.randomUUID() + "." + fileExtension;
            Path filePath = storageDir.resolve(uniqueFileName);

            // 保存文件
            file.transferTo(filePath.toFile());
            log.info("文件上传成功:原文件{} 保存至{}", originalFileName, filePath);

            return filePath.toString();
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 进行术语提取操作
     * */
    private Map<String, Object> processTermExtraction(String path, Long operationId) {
        Map<String, Object> result = new HashMap<>();
        File f = new File(path);
        String fileName = f.getName();
        String originalName = getOriginalFileName(fileName);
        result.put("fileName", originalName);

        try {
            List<TextSegment> segments = parseAndEmbedding(path);
            String fileText = segments.stream().map(TextSegment::text).collect(Collectors.joining("\n"));

            if (fileText != null && !fileText.isEmpty() && !fileText.contains("未检测到文字")) {
                // 文档切分成块
                List<String> textChunks = splitTextByParagraph(fileText);
                int totalChunks = textChunks.size();
                log.info("文档已切分成 {} 个文本块，开始并发处理", totalChunks);

                // 并发处理所有文本块，使用线程安全的列表收集结果
                List<String> allTermsList = Collections.synchronizedList(new ArrayList<>());
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < totalChunks; i++) {
                    final int chunkIndex = i;
                    final String chunk = textChunks.get(i);
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        log.info("处理第 {} / {} 个文本块，文本长度: {}", chunkIndex + 1, totalChunks, chunk.length());
                        String terms = termExtractionAgent.chatWithTermTool(chunk);
                        if (terms != null && !terms.isEmpty()) {
                            allTermsList.add(terms);
                            log.info("第 {} 个文本块术语提取完成，术语长度: {}", chunkIndex + 1, terms.length());
                        }
                    }, executorService);
                    futures.add(future);
                }

                // 等待所有文本块处理完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                log.info("所有文本块术语提取完成，共提取 {} 个术语块", allTermsList.size());

                // 顺序执行向量化存储，保证数据一致性
                if (!allTermsList.isEmpty()) {
                    log.info("开始向量化存储，共 {} 个术语块", allTermsList.size());
                    for (int i = 0; i < allTermsList.size(); i++) {
                        String terms = allTermsList.get(i);
                        qdrantOperationTools.embeddingTermAndSave(terms);
                        log.info("第 {} / {} 个术语块向量化完成", i + 1, allTermsList.size());
                    }
                }

                // 合并所有术语结果
                String termsResult = String.join("\n", allTermsList).trim();
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

    /**
     * 按篇章段落切分
     * */
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
            } else if (currentChunk.length() + paragraph.length() + 1 <= BusinessConstant.MAX_PARAGRAPH_LENGTH) {
                currentChunk.append("\n").append(paragraph);
            } else {
                chunks.add(currentChunk.toString().trim());
                // 某段内容超过最大长度，则进行语义切分处理
                if (paragraph.length() > BusinessConstant.MAX_PARAGRAPH_LENGTH) {
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

    /**
     * 超过最大长度的文本进行语义切分
     * */
    private List<String> splitLongText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + BusinessConstant.MAX_PARAGRAPH_LENGTH, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
