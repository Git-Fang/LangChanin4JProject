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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
public class AiConf {

    @Autowired
    @Qualifier("allMiniLmL6V2EmbeddingModel")
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
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddedModel)
                .embeddingStore(embeddingStore)
                .maxResults(10)
                .minScore(0.4)
                .build();
    }


    /**
     * 创建百度地图MCP服务实例（流式）
     * 仅在非Docker环境下启用（需要Windows cmd命令）
     * */
    @Bean
    @ConditionalOnProperty(name = "mcp.enabled", havingValue = "true", matchIfMissing = false)
    BaiduMapMcpStreamAssistant baiduMapMcpStreamAssistant() {

        return buildGenericMcpAssistant(BaiduMapMcpStreamAssistant.class, streamingChatModel);
    }

    /**
     * 创建百度地图MCP服务实例（非流式）
     * 仅在非Docker环境下启用（需要Windows cmd命令）
     * */
    @Bean
    @ConditionalOnProperty(name = "mcp.enabled", havingValue = "true", matchIfMissing = false)
    BaiduMapMcpAssistant baiduMapMcpAssistant() {

        return  buildGenericMcpAssistant(BaiduMapMcpAssistant.class, chatModel);
    }

    /**
     * Docker环境下的占位符Bean
     * */
    @Bean("baiduMapMcpAssistant")
    @ConditionalOnProperty(name = "mcp.enabled", havingValue = "false", matchIfMissing = true)
    BaiduMapMcpAssistant baiduMapMcpAssistantPlaceholder() {
        return userMessage -> "MCP服务在Docker环境中不可用";
    }


    /**
     * 使用泛型方式创建MCP工具提供者，支持不同类型的助手
     */
    private <T> T buildGenericMcpAssistant(Class<T> assistantClass, Object model) {
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

        // 3.根据传入的助手类型创建对应的AI服务实例
        AiServices<T> aiServices = AiServices.builder(assistantClass).toolProvider(toolProvider);
        if(model instanceof ChatModel){
            aiServices.chatModel((ChatModel) model);
        } else if(model instanceof StreamingChatModel){
            aiServices.streamingChatModel((StreamingChatModel) model);
        }
        return aiServices.build();
    }


}
