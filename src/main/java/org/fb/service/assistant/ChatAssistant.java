package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * 使用termQueryTool替代commonTools，避免误触发翻译功能
 * termQueryTool仅用于RAG检索场景的术语查询，不涉及翻译
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"termQueryTool","mongoDBTools","naturalLanguageSQLAgent","personalDataTools","webSearchTools","knowledgeBaseRetrievalService"},
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface ChatAssistant {
    String chat(String userMessage);

    @SystemMessage(fromResource = "default-prompt.txt")
    @UserMessage("结合知识库相关知识回答：{{question}}")
    public String chat(@MemoryId long memoryId,  @V("question") String question);
}
