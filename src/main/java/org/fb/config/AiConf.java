package org.fb.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import jakarta.annotation.PostConstruct;
import org.fb.constant.BusinessConstant;
import org.fb.service.assistant.BaiduMapMcpAssistant;
import org.fb.service.assistant.BaiduMapMcpStreamAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
public class AiConf {

    @Autowired
    private EmbeddingModel embeddedModel;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EnvConf envConf;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    @Qualifier("chatModel")
    private ChatModel chatModel;

    @PostConstruct
    public void createCollection() throws IOException {
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setDistance(Collections.Distance.Cosine)
                .setSize(embeddedModel.dimension())
                .build();
        qdrantClient.createCollectionAsync(envConf.collectionName, vectorParams);
    }


    /**
     * 定义向量数据库操作信息：指定嵌入模型、存储工具、查询结果数、最小得分阈值等信息
     * */
    @Bean
    ContentRetriever contentRetriever() {
        // 创建一个 EmbeddingStoreContentRetriever 对象，用于从嵌入存储中检索内容
        return EmbeddingStoreContentRetriever.builder()
                // 设置用于生成嵌入向量的嵌入模型
                .embeddingModel(embeddedModel)
                // 指定要使用的嵌入存储
                .embeddingStore(embeddingStore)
                // 设置最大检索结果数量，这里表示最多返回 1 条匹配结果
                .maxResults(1)
                // 设置最小得分阈值，只有得分大于等于 0.8 的结果才会被返回
                .minScore(0.8)
                // 构建最终的 EmbeddingStoreContentRetriever 实例
                .build();
    }


    /**
     * 创建百度地图MCP服务实例（流式）
     * */
    @Bean
    BaiduMapMcpStreamAssistant baiduMapMcpStreamAssistant() {

        McpToolProvider toolProvider = buildBaiduMapMcpTool();

        // 将streamingChatModel和工具提供者注入MCP助手
        return AiServices.builder(BaiduMapMcpStreamAssistant.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(toolProvider)
                .build();
    }



    /**
     * 创建百度地图MCP服务实例（非流式）
     * */
    @Bean
    BaiduMapMcpAssistant baiduMapMcpAssistant() {

        McpToolProvider toolProvider = buildBaiduMapMcpTool();

        // 将chatModel和工具提供者注入MCP助手
        return AiServices.builder(BaiduMapMcpAssistant.class)
                .chatModel(chatModel)
                .toolProvider(toolProvider)
                .build();
    }





    private McpToolProvider buildBaiduMapMcpTool() {
        // 1.启动百度地图MCP服务
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("cmd", "/c", "npx", "-y", BusinessConstant.BAIDU_MAP_MCP_SERVER))
                .environment(Map.of("BAIDU_MAP_API_KEY", envConf.baiduMapApiKey))
                .logEvents(true) // only if you want to see the traffic in the log
                .build();

        // 2.初始化MCP client、实例MCP工具提供者对象
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();

        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
        return toolProvider;
    }
}
