package org.fb.service.impl;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import org.fb.constant.BusinessConstant;
import org.fb.service.DocumentService;
import org.fb.service.assistant.ChatAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentImpl implements DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentImpl.class);

    @Autowired
    private EmbeddingModel embeddedModel;

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    private static final String DOC_FilePath0 = "D:\\个人资料\\java后端求职简历--方彪--251202.pdf";
    private static final String DOC_FilePath1 = "D:\\个人资料\\java求职简历-方彪--250927.pdf";
    private static final String DOC_FilePath2 = "D:\\个人资料\\方彪-离职证明.pdf";

    private static final String storagePath = BusinessConstant.TEMP_FILE_PATH;


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

        int batchSize = 10;
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);

            List<Embedding> embeddings = embeddedModel.embedAll(batch).content();

            embeddingStore.addAll(embeddings, batch);
        }
        return segments;
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

    // 获取文件扩展名的辅助方法
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "unknown";
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
}
