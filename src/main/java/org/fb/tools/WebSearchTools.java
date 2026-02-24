package org.fb.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络搜索工具类
 * 使用 DuckDuckGo Instant Answer API 进行网络搜索
 * 免费、无需 API Key
 */
@Component
public class WebSearchTools {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private static final String DDG_API_URL = "https://api.duckduckgo.com/";
    
    @Autowired
    private WebClient.Builder webClientBuilder;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        try {
            // 使用 DuckDuckGo Instant Answer API
            String apiUrl = DDG_API_URL + "?q=" + java.net.URLEncoder.encode(searchQuery, "UTF-8") 
                    + "&format=json&no_html=1&skip_disambig=1&ia=web";
            
            log.info("【API URL】: {}", apiUrl);

            String response = webClientBuilder.build()
                    .get()
                    .uri(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
     * 解析 DuckDuckGo API 返回的 JSON 结果
     */
    private String parseSearchResults(String jsonResponse, String searchQuery) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // 检查是否有即时答案（Instant Answer）
            JsonNode abstractNode = rootNode.path("AbstractText");
            if (!abstractNode.isMissingNode() && !abstractNode.asText().isEmpty()) {
                String abstractText = abstractNode.asText();
                String abstractSource = rootNode.path("AbstractSource").asText("未知来源");
                
                log.info("【即时答案】: {}", abstractText.substring(0, Math.min(100, abstractText.length())));
                
                StringBuilder result = new StringBuilder();
                result.append("【即时答案】\n");
                result.append(abstractText).append("\n\n");
                result.append("来源: ").append(abstractSource).append("\n");
                
                // 如果有相关主题，也添加进去
                JsonNode relatedTopics = rootNode.path("RelatedTopics");
                if (!relatedTopics.isMissingNode() && relatedTopics.isArray() && relatedTopics.size() > 0) {
                    result.append("\n【相关链接】\n");
                    int count = 0;
                    for (JsonNode topic : relatedTopics) {
                        if (count >= 5) break;
                        String text = topic.path("Text").asText();
                        String url = topic.path("Url").asText();
                        if (!text.isEmpty() && !url.isEmpty()) {
                            result.append("- ").append(text).append("\n");
                            result.append("  链接: ").append(url).append("\n");
                            count++;
                        }
                    }
                }
                
                return result.toString();
            }

            // 如果没有即时答案，解析 RelatedTopics
            JsonNode relatedTopics = rootNode.path("RelatedTopics");
            if (!relatedTopics.isMissingNode() && relatedTopics.isArray() && relatedTopics.size() > 0) {
                StringBuilder result = new StringBuilder();
                result.append("搜索关键词: ").append(searchQuery).append("\n\n");
                result.append("【搜索结果】\n");
                
                int count = 0;
                int maxResults = 8;
                
                for (JsonNode topic : relatedTopics) {
                    if (count >= maxResults) break;
                    
                    String text = topic.path("Text").asText();
                    String url = topic.path("Url").asText();
                    
                    // 跳过空条目
                    if (text.isEmpty() || url.isEmpty()) continue;
                    
                    // DuckDuckGo 相关主题通常以 "T" 开头的图标，筛选掉
                    if (text.startsWith("T ")) continue;
                    
                    log.info("【结果{}】: {} - {}", count + 1, text, url);
                    
                    result.append("【结果").append(++count).append("】\n");
                    result.append(text).append("\n");
                    result.append("链接: ").append(url).append("\n\n");
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
