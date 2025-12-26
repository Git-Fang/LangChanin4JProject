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
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.ChatAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/lc4j")
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

    @PostConstruct
    public void createCollection() throws IOException {
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setDistance(Collections.Distance.Cosine)
                .setSize(embeddedModel.dimension())
                .build();
        qdrantClient.createCollectionAsync("wx-estate", vectorParams);
    }

    private static final String DOC_FilePath = "D:\\个人资料\\java后端求职简历--方彪--251202.pdf";

    @GetMapping("/embeddingFile")
    @Operation(summary = "添加数据")
    public String embeddingFile() {
        String str = "咏鸭 鸭子嘎嘎嘎，肉食石子花。";

        TextSegment segment1 = TextSegment.from(str);
        segment1.metadata().put("author","fb");

        Response<Embedding> embed = embeddedModel.embed(str);
        embeddingStore.add(str, embed.content());

        return embed.content().toString();
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

    @GetMapping(value = "/rag01/add")
    @Operation(summary = "1-向量数据库添加文档")
    public String add() throws IOException {
        // 读取文档
        Document document = FileSystemDocumentLoader.loadDocument(DOC_FilePath, new ApacheTikaDocumentParser());
        document.metadata().put("author", "paperfly");
        // 2. 按段落切分
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(500, 50);
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
        return "Inserted " + segments.size() + " chunks into Qdrant";
    }

    @GetMapping(value = "/rag01/ask")
    @Operation(summary = "2-直接查询")
    public Object ask(String question) throws IOException {
        return chatAssistant.chat(question);
    }


    /*=======================以下只是从向量数据库查询获取相似向量数据的动作（与LLM的返回不是一回事）==========================*/

    @GetMapping(value = "/rag01/query")
    @Operation(summary = "3-基于向量数据库查询")
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
    @Operation(summary = "4-基于向量数据库查询2")
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
