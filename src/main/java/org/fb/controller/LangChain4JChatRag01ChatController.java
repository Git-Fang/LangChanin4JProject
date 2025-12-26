package org.fb.controller;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.assistant.ChatAssistant;
import org.fb.service.impl.DocumentImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/ragTranslation")
@Slf4j
@Tag(name = "9013--RAG增强检索测试")
public class LangChain4JChatRag01ChatController {

    @Autowired
    private EmbeddingModel embeddedModel;

    @Autowired
    private ChatAssistant chatAssistant;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private DocumentImpl documentImpl;

    @GetMapping(value = "/rag01/chat")
    @Operation(summary = "0-增强式对话")
    public Object ask(@RequestParam("question") String question) throws IOException {
        try {
            return chatAssistant.chat(question);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("textSegment cannot be null")) {
                // 记录错误并返回友好错误信息
                log.error("Embedding store contains null text segments, please check your data", e);
                return ResponseEntity.badRequest()
                        .body("暂时无法处理您的请求，请稍后重试");
            }
            throw e;
        }
    }


    @PostMapping("/uploadAndEmbeddingMultipleDocuments")
    @Operation(summary = "1-上传多个文档并向量存储")
    public String uploadAndEmbeddingMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        Integer sucessCount = documentImpl.saveAndEmbedding(files);
        log.info("成功上传并嵌入{}个文件", sucessCount);

        return "操作成功个数： " + sucessCount + "/" + files.length;
    }

    @PostMapping(value = "/rag01/add")
    @Operation(summary = "2-读取本地文档解析至向量数据库")
    public String add(@RequestParam("filePath") String filePath) throws IOException {
        List<TextSegment> segments = null;
        try {
            segments = documentImpl.parseAndEmbedding(filePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "Inserted " + segments.size() + " chunks into Qdrant";
    }


    @PostMapping("/embeddingFile")
    @Operation(summary = "3-直接添加文本数据")
    public String embeddingFile(@RequestParam("content") String content) {

        try {
            documentImpl.addText(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "Content embedded and stored successfully";
    }


    @PostMapping("/uploadMultipleDocuments")
    @Operation(summary = "上传多个文档到本地")
    public Integer uploadMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        return documentImpl.saveFilesToLocal(files);
    }



}
