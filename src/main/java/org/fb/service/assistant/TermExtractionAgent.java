package org.fb.service.assistant;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 单次调用agent
 * chatModel = "ollamaChatModel",表示使用ollama模型
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"qdrantOperationTools", "commonTools"},
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface TermExtractionAgent {

    @SystemMessage(fromResource = "termExtractionAgent-prompt-template.txt")
    public String chat( @MemoryId long memoryId, @UserMessage String userMessage);


    @Tool(name = "term_exact", value="提取术语词汇:从传入数据{{question}}中提取符合规范的术语词汇")
    @SystemMessage(fromResource = "termExtractionAgent-prompt-template.txt")
    public String chatWithTermTool(  @UserMessage String userMessage);
}


