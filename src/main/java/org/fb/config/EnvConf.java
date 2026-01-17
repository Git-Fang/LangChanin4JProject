package org.fb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvConf {

    @Value("${ai.embeddingStore.qdrant.host:localhost}")
    public String qdrantHost;

    @Value("${ai.embeddingStore.qdrant.port:6334}")
    public Integer qdrantPort;

    @Value("${ai.embeddingStore.qdrant.collectionName:ragTranslation-1226}")
    public String collectionName;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    public String ollamaUrl;

    @Value("${ai.ollama.model:deepseek-r1:8b}")
    public String ollamaModel;

    @Value("${ai.dashscope.apiKey:demo}")
    public String dashscopeApiKey;

    @Value("${ai.dashscope.model:qwen-vl-max}")
    public String dashscopeModel;

    @Value("${ai.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    public String dashscopeUrl;

    @Value("${ai.mcp.baiduMap.apiKey:}")
    public String baiduMapApiKey;


    public String kimiApiKey="sk-iHnlk244Ic6mia74KyVl8r4dxuymAeWP6sKk2xiPj4BYSRAx";

    @Value("${ai.kimi.model:kimi-k2-turbo-preview}")
    public String kimiModel;

    @Value("${ai.kimi.base-url:https://api.moonshot.cn/v1}")
    public String kimiUrl;


    @Value("${ai.deepSeek.apiKey:}")
    public String deepSeekApiKey;

    @Value("${ai.deepSeek.model:deepseek-chat}")
    public String deepSeekModel;

    @Value("${ai.deepSeek.base-url:https://api.deepseek.com/v1}")
    public String deepSeekUrl;

}
