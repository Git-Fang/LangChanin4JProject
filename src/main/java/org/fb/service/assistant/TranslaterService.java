package org.fb.service.assistant;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/*
* chatModel = "ollamaChatModel",表示使用ollama模型
* */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"mongoDBTools", "termExtractionAgent",  "commonTools"},
        chatMemoryProvider = "chatMemoryProvider"

)
public interface TranslaterService {

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("结合向量数据中type为TERMSle的相似向量数据完成该次翻译：{{userMessage}}")
    String translate(String userMessage);

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("结合向量数据中type为TERMSle的相似向量数据完成该次翻译：{{userMessage}}")
    String translate(@MemoryId long memoryId,  @V("userMessage") String userMessage);

}
