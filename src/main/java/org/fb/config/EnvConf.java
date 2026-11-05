package org.fb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@RefreshScope
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
 
 
    @Value("${ai.kimi.apiKey:}")
    public String kimiApiKey;
 
    @Value("${ai.kimi.model:kimi-k2.6}")
    public String kimiModel;
 
    @Value("${ai.kimi.base-url:https://api.moonshot.cn/v1}")
    public String kimiUrl;
 
 
    @Value("${ai.deepSeek.apiKey:}")
    public String deepSeekApiKey;
 
    @Value("${ai.deepSeek.model:deepseek-v4.1-flash-1017}")
    public String deepSeekModel;
 
    @Value("${ai.deepSeek.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    public String deepSeekUrl;
}
