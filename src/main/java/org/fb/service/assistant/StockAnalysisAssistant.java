package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 股票价值分析助手
 * 使用AI对公司进行深度价值分析并给出投资建议
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "chatModel",
        tools = {"stockDataFetcher", "webSearchTools"},
        chatMemoryProvider = "chatMemoryProvider"
)
public interface StockAnalysisAssistant {

    @SystemMessage(fromResource = "stock-analysis-prompt.txt")
    @UserMessage("请分析公司：{{companyName}}")
    String analyzeStock(@MemoryId long memoryId, @V("companyName") String companyName);

    /**
     * 直接分析公司（不带memoryId）
     */
    @SystemMessage(fromResource = "stock-analysis-prompt.txt")
    String analyzeStockDirect(String userMessage);
}
