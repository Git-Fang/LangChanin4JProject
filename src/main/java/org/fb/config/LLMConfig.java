package org.fb.config;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.assistant.ChatAssistantStream;
import org.fb.tools.MongoChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class LLMConfig {

    @Autowired
    private EnvConf envConf;

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;


    @Bean
    public ChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(envConf.ollamaUrl)
                .modelName(envConf.ollamaModel)
                .temperature(0.8)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(envConf.dashscopeApiKey)
                .modelName(envConf.dashscopeModel)
                .logRequests(true)
                .logResponses(true)
                .baseUrl(envConf.dashscopeUrl)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(envConf.dashscopeApiKey)
                .modelName(envConf.dashscopeModel)
                .logRequests(true)
                .logResponses(true)
                .baseUrl(envConf.dashscopeUrl)
                .build();
    }

    @Bean("allMiniLmL6V2EmbeddingModel")
    public EmbeddingModel allMiniLmL6V2EmbeddingModel() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        return embeddingModel;
    }

    // 支持qdrant向量数据存储
    @Bean
    public QdrantClient qdrantClient() {
        QdrantClient client =
                new QdrantClient(
                        QdrantGrpcClient.newBuilder(envConf.qdrantHost, envConf.qdrantPort, false)
                                .build());

        return client;
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        EmbeddingStore<TextSegment> embeddingStore =
                QdrantEmbeddingStore.builder()
                        .host(envConf.qdrantHost)
                        .port(envConf.qdrantPort)
                        .collectionName(envConf.collectionName)
                        .build();
        return embeddingStore;
    }


//    @Bean
//    public ChatAssistant chatAssistant(ChatModel chatModel, EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
//
//        // 构建支撑检索曾强的EmbeddingStore工具实例
//        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(embeddingStore)
//                .embeddingModel(embeddingModel)
//                .maxResults(5).build();
//        return AiServices
//                .builder(ChatAssistant.class)
//                .chatModel(chatModel)
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
//                .contentRetriever(contentRetriever)
//                .tools("commonTools")
//                .build();
//    }

    @Bean
    public ChatAssistantStream chatAssistantStream(StreamingChatModel chatModel, EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {

        // 构建支撑检索曾强的EmbeddingStore工具实例
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5).build();
        return AiServices
                .builder(ChatAssistantStream.class)
                .streamingChatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(contentRetriever)
                .build();
    }


    @Bean
    ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(mongoChatMemoryStore)//配置持久化对象
                .build();
    }
}
