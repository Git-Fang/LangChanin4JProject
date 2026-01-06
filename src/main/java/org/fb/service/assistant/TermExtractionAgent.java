package org.fb.service.assistant;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 术语提取Agent - 无记忆模式，避免上下文干扰
 * chatModel = "chatModel",表示使用chatModel模型
 * 不使用chatMemoryProvider，确保每次提取都是独立的，不受之前对话影响
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"qdrantOperationTools", "commonTools"}
)
public interface TermExtractionAgent {

    @SystemMessage(fromResource = "termExtractionAgent-prompt-template.txt")
    public String chat(@UserMessage String userMessage);


    @Tool(name = "term_exact", value="提取术语词汇:从传入数据{{question}}中提取符合规范的术语词汇")
    @SystemMessage(fromResource = "termExtractionAgent-prompt-template.txt")
    public String chatWithTermTool(  @UserMessage String userMessage);
}


