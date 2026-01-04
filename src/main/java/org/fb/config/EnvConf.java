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

    @Value("${ai.ollama.base-url}")
    public String ollamaUrl;

    @Value("${ai.ollama.model}")
    public String ollamaModel;

    @Value("${ai.dashscope.apiKey}")
    public String dashscopeApiKey;

    @Value("${ai.dashscope.model}")
    public String dashscopeModel;

    @Value("${ai.dashscope.base-url}")
    public String dashscopeUrl;

    @Value("${ai.mcp.baiduMap.apiKey}")
    public String baiduMapApiKey;


    public String kimiApiKey="sk-iHnlk244Ic6mia74KyVl8r4dxuymAeWP6sKk2xiPj4BYSRAx";

    @Value("${ai.kimi.model}")
    public String kimiModel;

    @Value("${ai.kimi.base-url}")
    public String kimiUrl;


    @Value("${ai.deepSeek.apiKey}")
    public String deepSeekApiKey;

    @Value("${ai.deepSeek.model}")
    public String deepSeekModel;

    @Value("${ai.deepSeek.base-url}")
    public String deepSeekUrl;

}
