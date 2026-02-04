package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 个人助手Chat接口
 * 支持本地知识库(.agent/knowledge目录)和在线知识库(mysql/qdrant/mongoDB)检索
 *
 * 检索优先级：
 * 1. 先搜索本地知识库（.agent 和 knowledge 目录）
 * 2. 若本地知识库无结果，再搜索在线知识库（mysql/qdrant/mongoDB）
 * 3. 最后使用大模型通用知识回答
 *
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {
                "localKnowledgeTools",      // 本地知识库检索工具
                "commonTools",             // 向量数据库检索工具
                "mongoDBTools",            // MongoDB工具
                "naturalLanguageSQLAgent",   // MySQL数据库工具
                "personalDataTools"         // 个人数据查询工具
        },
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface ChatAssistant {
    String chat(String userMessage);

    @SystemMessage(fromResource = "default-prompt.txt")
    @UserMessage("{{question}}")
    public String chat(@MemoryId long memoryId, @V("question") String question);
}
