package org.fb.service;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "streamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface StreamingChatService {

    Flux<String> chat(@MemoryId long memoryId, @UserMessage String question);

    @UserMessage("{{text}}")
    Flux<String> chatStream(@V("text") String text);
}
