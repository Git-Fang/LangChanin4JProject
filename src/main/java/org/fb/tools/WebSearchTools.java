package org.fb.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 网络搜索工具类
 * 使用 Tavily Search API 进行网络搜索
 * 支持实时信息查询、新闻追踪等
 */
@Component
public class WebSearchTools {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    
    @Value("${TAVILY_API_KEY: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}")
    private String tavilyApiKey;
    
    @Value("${ai.tavily.max-results:8}")
    private int maxResults;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebSearchTools() {
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行网络搜索
     * 当需要查询最新信息、实时数据或知识库以外的内容时使用此工具
     *
     * @param searchQuery 搜索关键词或问题
     * @return 搜索结果，包含相关网页的内容摘要
     */
    @Tool(name = "web_search", value = "网络搜索工具:当需要查询最新信息、实时数据或知识库以外的内容时使用此工具进行网络搜索。根据{{searchQuery}}搜索互联网并返回相关结果")
    public String webSearch(@P(value = "searchQuery", required = true) String searchQuery) {
        log.info("========== WebSearchTools.webSearch 开始 ==========");
        log.info("【搜索关键词】: {}", searchQuery);

        // 检查 API Key 是否配置
        if (tavilyApiKey == null || tavilyApiKey.isEmpty()) {
            log.error("【错误】Tavily API Key 未配置，请检查配置项 ai.tavily.apiKey");
            return "网络搜索服务未配置，请联系管理员配置 Tavily API Key。\n\n" +
                   "提示: 需要配置环境变量 TAVILY_API_KEY 或在配置文件中设置 ai.tavily.apiKey";
        }

        try {
            // 构建请求体
            String requestBody = String.format(
                    "{\"query\": \"%s\", \"max_results\": %d, \"include_answer\": true, \"include_raw_content\": false}",
                    searchQuery.replace("\"", "\\\""), maxResults
            );
            
            log.info("【API URL】: {}", TAVILY_API_URL);

            String response = webClient.post()
                    .uri(TAVILY_API_URL)
                    .header("Authorization", "Bearer " + tavilyApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isEmpty()) {
                log.warn("【警告】API 返回为空");
                return "网络搜索返回为空，请稍后重试。";
            }

            // 解析 JSON 响应
            return parseSearchResults(response, searchQuery);

        } catch (Exception e) {
            log.error("【错误】网络搜索失败: {}", e.getMessage(), e);
            return "网络搜索出错: " + e.getMessage() + "\n\n请检查网络连接后重试。";
        }
    }

    /**
     * 解析 Tavily API 返回的 JSON 结果
     */
    private String parseSearchResults(String jsonResponse, String searchQuery) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // 检查是否有错误
            if (rootNode.has("error")) {
                String errorMsg = rootNode.path("error").asText("未知错误");
                log.error("【Tavily API 错误】: {}", errorMsg);
                return "网络搜索服务返回错误: " + errorMsg;
            }

            // 检查是否有即时答案（Answer）
            JsonNode answerNode = rootNode.path("answer");
            if (!answerNode.isMissingNode() && !answerNode.asText().isEmpty()) {
                String answerText = answerNode.asText();
                
                log.info("【即时答案】: {}", answerText.substring(0, Math.min(100, answerText.length())));
                
                StringBuilder result = new StringBuilder();
                result.append("【搜索答案】\n");
                result.append(answerText).append("\n\n");
                
                // 添加搜索结果
                JsonNode resultsNode = rootNode.path("results");
                if (!resultsNode.isMissingNode() && resultsNode.isArray() && resultsNode.size() > 0) {
                    result.append("【相关链接】\n");
                    int count = 0;
                    for (JsonNode resultItem : resultsNode) {
                        if (count >= maxResults) break;
                        
                        String title = resultItem.path("title").asText();
                        String url = resultItem.path("url").asText();
                        String content = resultItem.path("content").asText();
                        
                        if (!title.isEmpty() && !url.isEmpty()) {
                            result.append("【结果").append(++count).append("】\n");
                            result.append("标题: ").append(title).append("\n");
                            result.append("链接: ").append(url).append("\n");
                            if (!content.isEmpty()) {
                                result.append("摘要: ").append(content.substring(0, Math.min(200, content.length())));
                                if (content.length() > 200) result.append("...");
                                result.append("\n");
                            }
                            result.append("\n");
                        }
                    }
                }
                
                return result.toString();
            }

            // 如果没有即时答案，解析 results 数组
            JsonNode resultsNode = rootNode.path("results");
            if (!resultsNode.isMissingNode() && resultsNode.isArray() && resultsNode.size() > 0) {
                StringBuilder result = new StringBuilder();
                result.append("搜索关键词: ").append(searchQuery).append("\n\n");
                result.append("【搜索结果】\n");
                
                int count = 0;
                for (JsonNode resultItem : resultsNode) {
                    if (count >= maxResults) break;
                    
                    String title = resultItem.path("title").asText();
                    String url = resultItem.path("url").asText();
                    String content = resultItem.path("content").asText();
                    
                    // 跳过空条目
                    if (title.isEmpty() && url.isEmpty()) continue;
                    
                    log.info("【结果{}】: {} - {}", count + 1, title, url);
                    
                    result.append("【结果").append(++count).append("】\n");
                    if (!title.isEmpty()) {
                        result.append("标题: ").append(title).append("\n");
                    }
                    result.append("链接: ").append(url).append("\n");
                    if (!content.isEmpty()) {
                        result.append("摘要: ").append(content.substring(0, Math.min(200, content.length())));
                        if (content.length() > 200) result.append("...");
                        result.append("\n");
                    }
                    result.append("\n");
                }
                
                if (count == 0) {
                    result.append("未找到相关结果。");
                }
                
                return result.toString();
            }

            // 如果都没有结果，返回原始响应
            log.warn("【警告】未解析到有效结果");
            return "未找到与 '" + searchQuery + "' 相关的网络结果。\n\n" +
                   "提示: 您可以尝试使用更简单的关键词进行搜索。";

        } catch (Exception e) {
            log.error("【错误】解析搜索结果失败: {}", e.getMessage(), e);
            return "解析搜索结果失败: " + e.getMessage();
        }
    }
}
