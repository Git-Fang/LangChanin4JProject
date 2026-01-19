package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import org.fb.constant.BusinessConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Component
public class QdrantOperationTools {
    private static final Logger log = LoggerFactory.getLogger(QdrantOperationTools.class);

    private static final String storagePath = BusinessConstant.TEMP_FILE_PATH;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
    private EmbeddingModel embeddedModel;

    @Autowired
    private CommonTools commonTools;

    @Tool(name = "embedding_term_and_save", value = "文本内容向量化、查询与保存:将传入数据{{text}}先进行向量化然后进行查询；若相似度>=0.85则不保存，否则将内容保存写入qdrant向量数据库中。")
    public void embeddingTermAndSave(@P(value = "传入数据") String text) {

        EmbeddingSearchResult<TextSegment> searchResult = commonTools.getMatchWordsForTerms(text);
        
        if (searchResult.matches().isEmpty()) {
            log.info("未找到相似内容，开始保存新数据");
            saveTerms(text);
            return;
        }

        EmbeddingMatch<TextSegment> embeddingMatch = searchResult.matches().get(0);
        log.info("相似度得分：{}; 匹配结果：{}",embeddingMatch.score(),embeddingMatch.embedded().text());

        if(embeddingMatch.score() < 0.85){
            saveTerms(text);
        } else {
            log.info("相似度>=0.85，不保存重复数据");
        }
    }

    private void saveTerms(String text) {
        String fileName = "专业术语词";

        log.debug("开始向量化。传入数据：{}", text);
        String path = saveTxtContentToLocal(text,fileName);
        log.info("开始向量化。内容地址：{}", path);

        parseAndEmbeddingForTerms(path, fileName);
        log.info("向量化完成。");

        deleteFile(path);
    }

    private List<TextSegment> parseAndEmbeddingForTerms(String filePath, String fileName) {

        // 1.读取文档
        Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
        document.metadata().put("fileName", fileName);
        document.metadata().put("author", "fb");
        document.metadata().put("type", "TERMS");

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


    private List<TextSegment> parseAndEmbedding(String filePath, String fileName) {

        // 1.读取文档
        Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
        document.metadata().put("fileName", fileName);
        document.metadata().put("author", "fb");
        document.metadata().put("type", "VECTORIZE");

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


    /**
     * 存储单个文件
     */
    private String saveTxtContentToLocal(String text, String fileName) {
        Path storageDir = Paths.get(storagePath);
        try {
            // 创建目录（如果不存在）
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }
            if (text == null || text.isEmpty()) {
                log.error("文本内容为空，无法保存");
                return "";
            }

            // 生成唯一文件名
            String uniqueFileName = fileName + "_" + UUID.randomUUID().toString() + ".txt";
            Path filePath = storageDir.resolve(uniqueFileName);

            // 将文本内容写入文件
            Files.write(filePath, text.getBytes("UTF-8"));
            log.info("文本内容保存成功: 文件名{} 保存至{}", uniqueFileName, filePath);

            // 返回文件的URL信息
            String fileUrl = storagePath + uniqueFileName; // fileUrlPrefix需要在类中定义，如"/files/"
            return fileUrl;

        } catch (IOException e) {
            log.error("文本内容保存失败", e);
            throw new RuntimeException("文本内容保存失败", e);
        }
    }
}
