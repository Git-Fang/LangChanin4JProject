package org.fb.service.assistant;

import org.fb.config.DynamicStreamingChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import reactor.core.publisher.Flux;


/**
 * 流式调用agent
 * 配置了 contentRetriever 自动进行 RAG 检索
 * 同时注入 tools 支持手动调用知识库检索工具
 * 使用 DynamicStreamingChatModel 支持运行时模型切换
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "dynamicStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = {"knowledgeBaseRetrievalService", "commonTools", "personalDataTools", "mongoDBTools", "webSearchTools"}
)
public interface ChatAssistantStream {

    @SystemMessage(fromResource = "default-prompt.txt")
    public Flux<String> chat(@MemoryId long memoryId, @UserMessage String userMessage);

    @SystemMessage(fromResource = "default-prompt.txt")
    public Flux<String> chat( String userMessage);
}
