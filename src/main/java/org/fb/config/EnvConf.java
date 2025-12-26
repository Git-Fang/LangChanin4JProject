package org.fb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvConf {

    @Value("${ai.embeddingStore.qdrant.host}")
    public String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port}")
    public Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName}")
    public String collectionName;
}
