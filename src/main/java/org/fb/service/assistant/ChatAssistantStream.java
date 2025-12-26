package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;


/**
 * 流式调用agent
 * */
/*@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "streamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"
//        , tools = "commonTools"
//        , contentRetriever = "contentRetriever"
)*/
public interface ChatAssistantStream {

    public Flux<String> chat(@MemoryId long memoryId, @UserMessage String userMessage);

    public Flux<String> chat( String userMessage);
}
