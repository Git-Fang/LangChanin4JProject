package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
//        chatModel = "chatModel",
        chatModel = "ollamaChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface ChatTypeAssistant {
    String chat(String userMessage);

//    @SystemMessage(fromResource = "chat-type-prompt.txt")
    public String chat(@MemoryId long memoryId, @UserMessage String userMessage);
}
