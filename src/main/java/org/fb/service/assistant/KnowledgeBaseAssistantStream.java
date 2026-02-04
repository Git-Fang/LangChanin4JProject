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
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "streamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = {"commonTools", "mongoDBTools", "personalDataTools"}
)
public interface KnowledgeBaseAssistantStream {

    @SystemMessage(fromResource = "knowledge-base-prompt.txt")
    @UserMessage("{{userMessage}}")
    public Flux<String> chat(@MemoryId long memoryId, @V("userMessage") String userMessage);

    @SystemMessage(fromResource = "knowledge-base-prompt.txt")
    @UserMessage("{{userMessage}}")
    public Flux<String> chat(@V("userMessage") String userMessage);
}
