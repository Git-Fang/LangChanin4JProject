package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"commonTools","mongoDBTools","naturalLanguageSQLAgent","personalDataTools"},
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface ChatAssistant {
    String chat(String userMessage);

    @SystemMessage(fromResource = "default-prompt.txt")
    @UserMessage("{{question}}")
    public String chat(@MemoryId long memoryId,  @V("question") String question);
}
