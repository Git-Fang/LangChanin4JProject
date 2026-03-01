package org.fb.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.fb.tools.StockDataFetcher;
import org.fb.tools.WebSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 股票价值分析服务
 * 负责对公司进行深度价值分析并给出投资建议
 */
@Service
public class StockAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(StockAnalysisService.class);

    @Autowired
    @Qualifier("chatModel")
    private ChatModel chatModel;

    @Autowired
    private StockDataFetcher stockDataFetcher;

    @Autowired
    private WebSearchTools webSearchTools;

    private String systemPrompt;

    /**
     * 初始化系统提示词
     */
    public StockAnalysisService() {
        try {
            ClassPathResource resource = new ClassPathResource("stock-analysis-prompt.txt");
            this.systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("股票分析系统提示词加载成功，长度: {}", systemPrompt.length());
        } catch (IOException e) {
            log.error("加载股票分析提示词失败: {}", e.getMessage(), e);
            this.systemPrompt = getDefaultPrompt();
        }
    }

    /**
     * 分析股票
     * @param memoryId 对话ID
     * @param userMessage 用户消息（包含公司名称）
     * @return 分析结果
     */
    public String analyzeStock(Long memoryId, String userMessage) {
        log.info("========== 股票分析开始 ==========");
        log.info("用户消息: {}", userMessage);

        try {
            // 提取公司名称
            String companyName = extractCompanyName(userMessage);
            if (companyName == null || companyName.isEmpty()) {
                return "请提供要分析的公司名称，例如：'请帮我分析公司：贵州茅台'";
            }

            log.info("提取到公司名称: {}", companyName);

            // 第一步：获取股票基本信息
            String stockQuote = stockDataFetcher.getStockQuote(companyName);
            log.info("股票行情信息获取完成");

            // 第二步：获取公司基本信息
            String companyInfo = stockDataFetcher.getStockCompanyInfo(companyName);
            log.info("公司基本信息获取完成");

            // 第三步：使用网络搜索获取更多财务和新闻信息
            String searchResults = webSearchTools.webSearch(
                    companyName + " 股票 财务报告 2024 2025 投资分析"
            );
            log.info("网络搜索完成");

            // 第四步：构建分析请求
            String analysisRequest = buildAnalysisRequest(companyName, stockQuote, companyInfo, searchResults);

            // 第五步：调用AI进行分析
            log.info("开始调用AI进行股票分析...");
            String analysisResult = chatWithAI(analysisRequest);

            log.info("========== 股票分析完成 ==========");
            return analysisResult;

        } catch (Exception e) {
            log.error("股票分析失败: {}", e.getMessage(), e);
            return "抱歉，分析股票时出现错误：" + e.getMessage();
        }
    }

    /**
     * 提取公司名称
     */
    private String extractCompanyName(String message) {
        // 尝试匹配 "分析公司：xxx" 格式
        Pattern pattern = Pattern.compile("(?:分析|帮我分析|请分析|分析一下)?公司[：:]?\\s*([^\n，。,，]+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 尝试匹配 "xxx股票" 格式
        pattern = Pattern.compile("([^\n，。,，]+)\\s*股票");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果没有匹配到任何格式，返回整个消息作为公司名称
        return message.trim();
    }

    /**
     * 构建分析请求
     */
    private String buildAnalysisRequest(String companyName, String stockQuote, String companyInfo, String searchResults) {
        StringBuilder request = new StringBuilder();
        request.append("请对【").append(companyName).append("】进行深度价值分析。\n\n");

        request.append("【获取到的股票行情信息】\n");
        request.append(stockQuote).append("\n\n");

        request.append("【获取到的公司基本信息】\n");
        request.append(companyInfo).append("\n\n");

        request.append("【网络搜索获取的相关信息】\n");
        request.append(searchResults).append("\n\n");

        request.append("请根据以上信息，按照以下11个维度进行深度分析并给出投资建议：\n");
        request.append("1. 业务简单性（公司是做什么的，是否容易理解）\n");
        request.append("2. 经济模型（盈利能力、成长能力、营运能力、偿债能力）\n");
        request.append("3. 商业模式（公司如何赚钱，是否可持续）\n");
        request.append("4. 企业基因与企业文化\n");
        request.append("5. 护城河（竞争优势）\n");
        request.append("6. 管理团队\n");
        request.append("7. PESTEL分析\n");
        request.append("8. 波特五力分析\n");
        request.append("9. 安全边际（估值分析）\n");
        request.append("10. 第二层思维（为什么可能被低估）\n");
        request.append("11. 致命风险\n\n");

        request.append("请对每个维度进行1-5分评分，最后给出综合投资建议。\n");
        request.append("注意：当前日期是2026年3月，请使用最新的数据进行评估。\n");
        request.append("如果某些数据无法获取，请明确说明。");

        return request.toString();
    }

    /**
     * 调用AI进行分析
     */
    private String chatWithAI(String userMessage) {
        try {
            // 使用LangChain4j的AI服务
            var assistant = AiServices.builder(StockAnalysisAssistant.class)
                    .chatModel(chatModel)
                    .tools(stockDataFetcher, webSearchTools)
                    .systemMessageProvider(memoryId -> systemPrompt)
                    .build();

            return assistant.analyzeStockDirect(userMessage);
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage(), e);
            // 如果AI服务失败，使用简单的ChatModel
            return chatWithSimpleModel(userMessage);
        }
    }

    /**
     * 使用简单的ChatModel进行聊天
     */
    private String chatWithSimpleModel(String userMessage) {
        try {
            return chatModel.chat(systemPrompt + "\n\n用户问题：" + userMessage);
        } catch (Exception e) {
            log.error("简单模型调用也失败: {}", e.getMessage(), e);
            return "抱歉，AI服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 获取默认提示词
     */
    private String getDefaultPrompt() {
        return """
                # 股票价值分析专家系统提示

                ## 角色定义
                你是一位拥有全球视野的资深价值投资者，具备深厚的财务分析能力、估值建模经验和行业研究能力。

                ## 你的任务
                请对指定的公司进行深度的价值分析，对每一个维度进行评分（1-5分，满分5分），最后给出综合的投资建议以及后续关注点。

                ## 分析维度
                1. 业务简单性
                2. 公司的经济模型（ROE、毛利率、净利率、增长率等）
                3. 商业模式
                4. 企业文化
                5. 护城河
                6. 管理团队
                7. PESTEL分析
                8. 波特五力分析
                9. 安全边际（DCF估值）
                10. 第二层思维
                11. 致命风险

                ## 输出要求
                请提供详细的分析报告，包括每个维度的评分和理由，最后给出投资建议。
                """;
    }

    /**
     * AI服务接口（内部使用）
     */
    public interface StockAnalysisAssistant {
        String analyzeStockDirect(String userMessage);
    }
}
