package org.fb.service.assistant;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever"
)
public interface NaturalLanguageSQLAgent {

    @SystemMessage("你是一个MySQL数据库专家，负责将自然语言转换为SQL查询语句。")
    @UserMessage("{{prompt}}")
    String convertToSQL(@V("prompt") String prompt);


    @UserMessage("{{prompt}}")
    @SystemMessage("你是一个MySQL数据库专家，负责将自然语言转换为SQL语句，并正确执行该sql与返回结果。")
    @Tool(name = "sql_convert", value = "sql操作:将传入数据{{question}}转为相关sql并执行返回结果。")
    String doSQL(@V("question") String question);
}