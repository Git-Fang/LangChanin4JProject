package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = "chatInfoTools",
        contentRetriever = "contentRetriever"
)
public interface ChatTypeAssistant {
    String chat(String userMessage);

    @SystemMessage("对输入信息首先进行意图分析，然后将相关数据转为ChatInfo类对应的数据结构后调用chatInfoTools中的saveChatInfo工具方法保存写入chatInfo表。")
    public String chat(@MemoryId long memoryId, @UserMessage String userMessage);
}
