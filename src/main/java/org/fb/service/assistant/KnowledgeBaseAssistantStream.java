package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import reactor.core.publisher.Flux;


/**
 * 知识库问答助手流式服务
 * 专门用于处理知识库问答类型的对话
 * 支持本地知识库(.agent/knowledge目录)和在线知识库检索
 *
 * 检索优先级：
 * 1. 先搜索本地知识库（.agent 和 knowledge 目录）
 * 2. 若本地知识库无结果，再搜索在线知识库（mysql/qdrant/mongoDB）
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "streamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = {
                "localKnowledgeTools",      // 本地知识库检索工具
                "commonTools",              // 向量数据库检索工具
                "mongoDBTools",             // MongoDB工具
                "personalDataTools"          // 个人数据查询工具
        }
)
public interface KnowledgeBaseAssistantStream {

    @SystemMessage(fromResource = "knowledge-base-prompt.txt")
    @UserMessage("{{userMessage}}")
    public Flux<String> chat(@MemoryId long memoryId, @V("userMessage") String userMessage);

    @SystemMessage(fromResource = "knowledge-base-prompt.txt")
    @UserMessage("{{userMessage}}")
    public Flux<String> chat(@V("userMessage") String userMessage);
}
