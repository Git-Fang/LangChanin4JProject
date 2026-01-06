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
        // 1.读取文档
        Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
        document.metadata().put("author", "fb");

        // 2. 按段落切分
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(800, 80);
        List<TextSegment> segments = splitter.split(document);

        // 3. 分批调用 embedding（一次最多 10 条）
        int batchSize = 10;
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);

            // 调用 embedding API
            List<Embedding> embeddings = embeddedModel.embedAll(batch).content();

            // 存入向量数据库
            embeddingStore.addAll(embeddings, batch);
        }
        return segments;
    }




    // 校验文件类型是否支持
    private boolean isValidDocumentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return extension.equals("pdf") ||
                extension.equals("txt") ||
                extension.equals("md") ||
                extension.equals("doc") ||
                extension.equals("docx") ||
                extension.equals("pptx");
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
