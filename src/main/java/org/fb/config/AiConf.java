package org.fb.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class AiConf {

    @Autowired
    private EmbeddingModel embeddedModel;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private EnvConf envConf;

    @PostConstruct
    public void createCollection() throws IOException {
        Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                .setDistance(Collections.Distance.Cosine)
                .setSize(embeddedModel.dimension())
                .build();
        qdrantClient.createCollectionAsync(envConf.collectionName, vectorParams);
    }
}
