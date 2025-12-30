package org.fb.service.assistant;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
//        chatModel = "chatModel",
        chatModel = "ollamaChatModel",
//        tools = "mongoDBTools",
        chatMemoryProvider = "chatMemoryProvider"

)
public interface TranslaterService {

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("{{userMessage}}")
    String translate(String userMessage);

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("{{userMessage}}")
    String translate(@MemoryId long memoryId,  @V("userMessage") String userMessage);

}
