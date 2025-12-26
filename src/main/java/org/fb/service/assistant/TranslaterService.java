package org.fb.service.assistant;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        chatMemoryProvider = "chatMemoryProvider"
)
public interface TranslaterService {

    @SystemMessage(fromResource = "transPrompt.txt")
    @UserMessage("用户输入的待翻译操作内容为：{{userMessage}}")
    String translate(String userMessage);

}
