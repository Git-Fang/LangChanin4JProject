package org.fb.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 股票数据获取工具
 * 用于获取股票的基本行情、财务报表、核心经营数据等信息
 */
@Component
public class StockDataFetcher {
    private static final Logger log = LoggerFactory.getLogger(StockDataFetcher.class);
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool(name = "get_stock_quote", value = "获取股票基本行情信息:根据股票代码或公司名称获取当前股价、涨跌幅、成交量、市值等基本信息")
    public String getStockQuote(@P(value = "companyName", required = true) String companyName) {
        log.info("========== get_stock_quote 开始 ==========");
        log.info("查询公司: {}", companyName);
        
        try {
            // 使用东方财富接口获取股票行情
            // 这里使用免费接口，实际生产环境可能需要付费API
            String url = "https://searchapi.eastmoney.com/api/suggest/get?input=" + 
                    java.net.URLEncoder.encode(companyName, "UTF-8") + 
                    "&type=14&token=43c1516cd3a45a03f7c13e8161843031&count=5";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("API响应状态: {}, 长度: {}", response.statusCode(), response.body().length());
            
            if (response.statusCode() == 200) {
                return parseStockQuoteResponse(response.body(), companyName);
            } else {
                return "查询失败，HTTP状态码: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("获取股票行情失败: {}", e.getMessage(), e);
            return "获取股票行情失败: " + e.getMessage() + "。请尝试使用网络搜索获取更多信息。";
        }
    }

    @Tool(name = "get_stock_financial_data", value = "获取股票财务数据:根据股票代码获取公司的财务报表数据，包括资产负债表、利润表、现金流量表等关键财务指标")
    public String getStockFinancialData(@P(value = "stockCode", required = true) String stockCode) {
        log.info("========== get_stock_financial_data 开始 ==========");
        log.info("查询股票代码: {}", stockCode);
        
        try {
            // 这里使用示例数据，实际生产环境需要调用专业的财务数据API
            // 例如：东方财富、同花顺、Wind等
            
            String url = "https://emweb.securities.eastmoney.com/PC_HSF10/FinancialAnalysis/MainTargetAjax?code=" + stockCode;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("API响应状态: {}, 长度: {}", response.statusCode(), response.body().length());
            
            if (response.statusCode() == 200) {
                return parseFinancialDataResponse(response.body(), stockCode);
            } else {
                return "查询失败，HTTP状态码: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("获取财务数据失败: {}", e.getMessage(), e);
            return "获取财务数据失败: " + e.getMessage() + "。请尝试使用网络搜索获取更多信息。";
        }
    }

    @Tool(name = "get_stock_company_info", value = "获取公司基本信息:根据股票代码或公司名称获取公司的主营业务、主要产品、行业地位等基本信息")
    public String getStockCompanyInfo(@P(value = "companyName", required = true) String companyName) {
        log.info("========== get_stock_company_info 开始 ==========");
        log.info("查询公司: {}", companyName);
        
        try {
            // 使用新浪财经接口获取公司基本信息
            // 搜索获取股票代码
            String searchUrl = "https://searchapi.eastmoney.com/api/suggest/get?input=" + 
                    java.net.URLEncoder.encode(companyName, "UTF-8") + 
                    "&type=14&token=43c1516cd3a45a03f7c13e8161843031&count=1";
            
            HttpRequest searchRequest = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            
            HttpResponse<String> searchResponse = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
            
            if (searchResponse.statusCode() == 200) {
                String stockCode = parseStockCode(searchResponse.body());
                if (stockCode != null) {
                    // 获取公司概况
                    String infoUrl = "https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/PageAjax?code=" + stockCode;
                    
                    HttpRequest infoRequest = HttpRequest.newBuilder()
                            .uri(URI.create(infoUrl))
                            .timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "Mozilla/5.0")
                            .GET()
                            .build();
                    
                    HttpResponse<String> infoResponse = httpClient.send(infoRequest, HttpResponse.BodyHandlers.ofString());
                    
                    if (infoResponse.statusCode() == 200) {
                        return parseCompanyInfoResponse(infoResponse.body(), companyName);
                    }
                }
            }
            
            return "未能获取到公司信息，请尝试使用网络搜索。";
        } catch (Exception e) {
            log.error("获取公司信息失败: {}", e.getMessage(), e);
            return "获取公司信息失败: " + e.getMessage() + "。请尝试使用网络搜索获取更多信息。";
        }
    }

    /**
     * 解析股票行情响应
     */
    private String parseStockQuoteResponse(String response, String companyName) {
        try {
            // 简单的JSON解析
            if (response.contains("\"Code\"")) {
                StringBuilder result = new StringBuilder();
                result.append("【").append(companyName).append("】股票行情信息：\n");
                
                // 这里简化处理，实际需要更完整的JSON解析
                result.append("注：由于API返回数据格式复杂，建议使用网络搜索获取实时行情数据。\n");
                result.append("搜索关键词建议：").append(companyName).append(" 股票 行情");
                
                return result.toString();
            }
            return "未能解析股票行情数据，请使用网络搜索获取更多信息。";
        } catch (Exception e) {
            log.warn("解析股票行情响应失败: {}", e.getMessage());
            return "解析股票行情数据失败，请使用网络搜索获取更多信息。";
        }
    }

    /**
     * 解析财务数据响应
     */
    private String parseFinancialDataResponse(String response, String stockCode) {
        try {
            if (response != null && response.length() > 0) {
                StringBuilder result = new StringBuilder();
                result.append("【股票代码：").append(stockCode).append("】财务数据：\n");
                result.append("注：财务数据获取需要更专业的API支持。\n");
                result.append("建议使用网络搜索获取最新的财务报告数据。");
                return result.toString();
            }
            return "未能获取财务数据，请使用网络搜索。";
        } catch (Exception e) {
            log.warn("解析财务数据响应失败: {}", e.getMessage());
            return "解析财务数据失败，请使用网络搜索获取更多信息。";
        }
    }

    /**
     * 解析股票代码
     */
    private String parseStockCode(String response) {
        try {
            // 简单的股票代码提取
            if (response.contains("sh60") || response.contains("sz00") || response.contains("sz30")) {
                // 匹配股票代码
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(sh60|sz00|sz30)\\d+");
                java.util.regex.Matcher matcher = pattern.matcher(response);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("解析股票代码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析公司信息响应
     */
    private String parseCompanyInfoResponse(String response, String companyName) {
        try {
            if (response != null && response.length() > 0) {
                StringBuilder result = new StringBuilder();
                result.append("【").append(companyName).append("】公司基本信息：\n");
                result.append("注：公司详细信息建议通过网络搜索获取最新资料。");
                return result.toString();
            }
            return "未能获取公司信息，请使用网络搜索。";
        } catch (Exception e) {
            log.warn("解析公司信息响应失败: {}", e.getMessage());
            return "解析公司信息失败，请使用网络搜索获取更多信息。";
        }
    }
}
