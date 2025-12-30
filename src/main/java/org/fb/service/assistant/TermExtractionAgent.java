package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 单次调用agent
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
//        chatModel = "chatModel",
        chatModel = "ollamaChatModel",
        chatMemoryProvider = "chatMemoryProvider",
//        tools = "qdrantOperationTools",
        contentRetriever = "contentRetriever"
)
public interface TermExtractionAgent {

    @SystemMessage(fromResource = "termExtractionAgent-prompt-template.txt")
    public String chat( @MemoryId long memoryId, @UserMessage String userMessage);
}


