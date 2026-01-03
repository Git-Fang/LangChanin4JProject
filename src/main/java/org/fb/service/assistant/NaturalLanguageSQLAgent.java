package org.fb.service.assistant;

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
        String convertToSQL( @V("prompt")String prompt);
    }