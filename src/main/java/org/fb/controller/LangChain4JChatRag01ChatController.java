package org.fb.controller;

import com.google.common.util.concurrent.ListenableFuture;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
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
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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


    @PostMapping("/uploadMultipleDocuments")
    @Operation(summary = "1-上传多个文档到本地")
    public Integer uploadMultipleDocuments(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        return documentImpl.saveFilesToLocal(files);
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






















    @GetMapping("/embeddingFile2")
    public String embeddingFile2() throws IOException, ExecutionException, InterruptedException {// 创建临时文件

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.{java,jsp,html,css,xml,properties}");

        List<Document> documents = FileSystemDocumentLoader.loadDocumentsRecursively(
                "D:/project/estate-wx-20241122/estate-wx",
                pathMatcher,
                new TextDocumentParser());
        //下面这个循环：主要是测试从向量数据库查询数据，然后删除（场景：更新文档了后，删除之前的向量数据）
        for (Document document : documents) {
            document.metadata().put("doc_id", document.metadata().getString("absolute_directory_path") + "\\" + document.metadata().getString("file_name"));

            // 构建查询条件
            Points.FieldCondition fieldCondition = Points.FieldCondition.newBuilder()
                    .setKey("file_name")
                    .setMatch(Points.Match.newBuilder().setText(document.metadata().getString("file_name")))
                    .build();
            Points.Condition condition = Points.Condition.newBuilder()
                    .setField(fieldCondition)
                    .build();
            Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(condition)
                    .build();

            //测试查询
            Points.ScrollPoints request = Points.ScrollPoints.newBuilder()
                    .setCollectionName("wx-estate")
                    .setFilter(filter)
                    .setLimit(5)
                    .build();
            Points.ScrollResponse scrollResponse = qdrantClient.scrollAsync(request).get();
            int resultCount = scrollResponse.getResultCount();
            System.out.println("resultCount:" + resultCount);

            //测试删除
            ListenableFuture<Points.UpdateResult> updateResultListenableFuture = qdrantClient.deleteAsync("wx-estate", filter);
            Points.UpdateResult updateResult = updateResultListenableFuture.get();
            System.out.println("updateResult:" + updateResult.getStatus());

        }

        // 通过ingestor将检索文档放入向量数据库
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(500, 50);
        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddedModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(splitter)
                .build()
                .ingest(documents);

        return "success";
    }


    /*=======================以下只是从向量数据库查询获取相似向量数据的动作（与LLM的返回不是一回事）==========================*/

    @GetMapping(value = "/rag01/query")
//    @Operation(summary = "3-基于向量数据库查询")
    public Object query(String question) throws IOException {
        // 查询条件向量化
        Embedding queryEmbedding = embeddedModel.embed(question).content();
        System.out.println("维度: " + queryEmbedding.vector().length);

        // 构建向量查询searchRequest,并查询
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(embeddingSearchRequest).matches();

        // 结果处理
        EmbeddingMatch<TextSegment> embeddingMatch = matches.get(0);
        System.out.println(embeddingMatch.score());
        System.out.println(embeddingMatch.embedded().text());
        return embeddingMatch.embedded().text();
    }

    @GetMapping(value = "/rag01/query2")
//    @Operation(summary = "4-基于向量数据库查询2")
    public Object query2(String  question) throws IOException {
        Embedding queryEmbedding = embeddedModel.embed(question).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
//                .filter(MetadataFilterBuilder.metadataKey("author").isEqualTo("fb"))
                .maxResults(1)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(embeddingSearchRequest).matches();

        EmbeddingMatch<TextSegment> embeddingMatch = matches.get(0);
        System.out.println(embeddingMatch.score());
        System.out.println(embeddingMatch.embedded().text());
        return embeddingMatch.embedded().text();
    }



}
