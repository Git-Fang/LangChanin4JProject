package org.fb.service.assistant;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/*
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * 使用translationTermExtractor替代termExtractionAgent，确保翻译过程中提取的术语不会被存入向量数据库
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"mongoDBTools", "translationTermExtractor", "commonTools"},
        chatMemoryProvider = "chatMemoryProvider"

)
public interface TranslaterService {

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("请基于向量数据中type为TERMS的过滤查询相似向量数据完成该次翻译：{{userMessage}}")
    String translate(String userMessage);

    @SystemMessage(fromResource = "translate-prompt.txt")
    @UserMessage("请基于向量数据中type为TERMS的过滤查询相似向量数据完成该次翻译：{{userMessage}}")
    String translate(@MemoryId long memoryId,  @V("userMessage") String userMessage);

}
