package org.fb.service.assistant;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 翻译专用术语提取Agent - 仅用于翻译场景提取术语，不进行任何向量存储操作
 * chatModel = "chatModel",表示使用chatModel模型
 * 只配置commonTools进行查询，不配置qdrantOperationTools以避免存储术语
 * */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"commonTools"}
)
public interface TranslationTermExtractor {

    @Tool(name = "term_exact_for_translation", value="【翻译专用】提取术语词汇:仅从传入数据{{question}}中提取符合规范的术语词汇，【禁止】进行任何向量存储操作")
    @SystemMessage(fromResource = "translation-term-extractor-prompt-template.txt")
    public String extractTerms(@UserMessage String userMessage);
}
