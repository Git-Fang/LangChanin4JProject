package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 意图识别服务
 * 用于识别用户对话的意图类型（translation/medical/general等）
 * 不使用contentRetriever，避免检索结果干扰意图判断
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = "chatInfoTools"
)
public interface ChatTypeAssistant {

    String chat(String userMessage);

    @SystemMessage(fromResource = "chat-type-prompt.txt")
    @UserMessage("{{userMessage}}")
    public String chat(@MemoryId long memoryId, @V("userMessage") String userMessage);
}
