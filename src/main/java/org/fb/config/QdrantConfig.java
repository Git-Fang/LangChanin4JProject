package org.fb.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant向量数据库配置类
 * 提供collection自动创建逻辑
 */
@Configuration
public class QdrantConfig {
    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    /**
     * allMiniLmL6V2模型的向量维度
     * 该模型输出384维的嵌入向量
     */
    private static final int EMBEDDING_DIMENSION = 384;

    @Value("${ai.embeddingStore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port:6334}")
    private Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName:doc-summarizer}")
    private String collectionName;

    @Value("${ai.embeddingStore.qdrant.timeout:30}")
    private Long timeoutSeconds;

    /**
     * 创建Qdrant gRPC客户端
     */
    @Bean
    public QdrantClient qdrantClient() {
        try {
            QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build();
            return new QdrantClient(grpcClient);
        } catch (Exception e) {
            log.error("Qdrant客户端初始化失败: {}", e.getMessage());
            throw new RuntimeException("无法连接Qdrant向量数据库，请确保Qdrant服务已启动", e);
        }
    }

    /**
     * 创建Qdrant向量存储实例
     * 包含collection自动创建逻辑
     */
    @Bean(name = "qdrantEmbeddingStore")
    public EmbeddingStore<TextSegment> qdrantEmbeddingStore() {
        try {
            // 首先检查并创建collection
            ensureCollectionExists();
            
            // 然后构建EmbeddingStore
            return QdrantEmbeddingStore.builder()
                    .host(qdrantHost)
                    .port(qdrantPort)
                    .collectionName(collectionName)
                    .build();
        } catch (Exception e) {
            log.error("Qdrant向量存储初始化失败: {}", e.getMessage());
            throw new RuntimeException("无法初始化Qdrant向量存储，请检查Qdrant服务配置", e);
        }
    }

    /**
     * 确保Qdrant collection存在
     * 如果不存在，自动创建
     */
    private void ensureCollectionExists() {
        log.info("检查Qdrant collection是否存在: {}", collectionName);
        
        try {
            QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build();
            QdrantClient client = new QdrantClient(grpcClient);
            
            // 检查collection是否存在
            Boolean exists = client.collectionExistsAsync(collectionName)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(exists)) {
                log.info("Qdrant collection '{}' 已存在", collectionName);
            } else {
                log.info("Qdrant collection '{}' 不存在，开始创建...", collectionName);
                createCollection(client);
            }
            
        } catch (Exception e) {
            log.warn("检查collection存在性失败，尝试直接创建: {}", e.getMessage());
            try {
                QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build();
                QdrantClient client = new QdrantClient(grpcClient);
                createCollection(client);
            } catch (Exception createError) {
                log.error("创建Qdrant collection失败: {}", createError.getMessage());
                throw new RuntimeException("无法创建Qdrant collection: " + collectionName, createError);
            }
        }
    }

    /**
     * 创建Qdrant collection
     * 配置向量维度为384（allMiniLmL6V2模型）
     * 使用Cosine距离度量
     */
    private void createCollection(QdrantClient client) {
        try {
            // 构建创建collection请求
            Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                    .setSize(EMBEDDING_DIMENSION)
                    .setDistance(Collections.Distance.Cosine)
                    .build();

            Collections.CreateCollection createCollectionRequest = Collections.CreateCollection.newBuilder()
                    .setCollectionName(collectionName)
                    .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                            .setParams(vectorParams)
                            .build())
                    .build();

            // 执行创建
            client.createCollectionAsync(createCollectionRequest)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            log.info("Qdrant collection '{}' 创建成功，维度={}, 距离度量=Cosine", 
                    collectionName, EMBEDDING_DIMENSION);

        } catch (Exception e) {
            log.error("创建Qdrant collection '{}' 失败: {}", collectionName, e.getMessage());
            throw new RuntimeException("无法创建Qdrant collection: " + collectionName, e);
        }
    }

    /**
     * 获取当前配置的collection名称
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * 获取向量维度
     */
    public int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }
}
