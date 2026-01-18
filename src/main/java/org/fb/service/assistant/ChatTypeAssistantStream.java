package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 流式意图识别服务
 * 用于在流式对话中识别用户意图
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "streamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface ChatTypeAssistantStream {

    @SystemMessage(fromResource = "chat-type-prompt.txt")
    @UserMessage("{{userMessage}}")
    Flux<String> chat(@MemoryId long memoryId, @V("userMessage") String userMessage);
}
