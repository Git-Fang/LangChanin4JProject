package org.fb.config;

import org.fb.service.assistant.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 当没有配置LLM模型时的 fallback 配置
 * 提供占位符 bean 以确保应用程序能够启动
 */
@Configuration
public class FallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(FallbackConfig.class);

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 ChatAssistant
     */
    @Bean
    @ConditionalOnMissingBean(ChatAssistant.class)
    public ChatAssistant chatAssistantFallback() {
        return new ChatAssistant() {
            @Override
            public String chat(String userMessage) {
                return "抱歉，聊天服务暂时不可用，请配置 LLM 模型后重试。";
            }

            @Override
            public String chat(long memoryId, String question) {
                return "抱歉，聊天服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 DoctorAgent
     */
    @Bean
    @ConditionalOnMissingBean(DoctorAgent.class)
    public DoctorAgent doctorAgentFallback() {
        return new DoctorAgent() {
            @Override
            public String chat(long memoryId, String question) {
                return "抱歉，医疗助手服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 TranslaterService
     */
    @Bean
    @ConditionalOnMissingBean(TranslaterService.class)
    public TranslaterService translaterServiceFallback() {
        return new TranslaterService() {
            @Override
            public String translate(String userMessage) {
                return "抱歉，翻译服务暂时不可用，请配置 LLM 模型后重试。";
            }

            @Override
            public String translate(long memoryId, String text) {
                return "抱歉，翻译服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 ChatTypeAssistant
     */
    @Bean
    @ConditionalOnMissingBean(ChatTypeAssistant.class)
    public ChatTypeAssistant chatTypeAssistantFallback() {
        return new ChatTypeAssistant() {
            @Override
            public String chat(String userMessage) {
                return "{\"intent\": \"general\"}";
            }

            @Override
            public String chat(long memoryId, String question) {
                return "{\"intent\": \"general\"}";
            }
        };
    }

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 TermExtractionAgent
     */
    @Bean
    @ConditionalOnMissingBean(TermExtractionAgent.class)
    public TermExtractionAgent termExtractionAgentFallback() {
        return new TermExtractionAgent() {
            @Override
            public String chat(String userMessage) {
                return "抱歉，术语提取服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }

    /**
     * 当没有配置 ChatModel 时，提供一个默认的 NaturalLanguageSQLAgent
     */
    @Bean
    @ConditionalOnMissingBean(NaturalLanguageSQLAgent.class)
    public NaturalLanguageSQLAgent naturalLanguageSQLAgentFallback() {
        return new NaturalLanguageSQLAgent() {
            @Override
            public String convertToSQL(String prompt) {
                log.info("使用 Fallback 的 convertToSQL 方法");
                log.debug("原始 prompt: {}", prompt);
                
                try {
                    String lowerPrompt = prompt.toLowerCase();
                    
                    // 检测是否是统计查询（查询数据数量）
                    if (lowerPrompt.contains("多少条") || lowerPrompt.contains("有多少") ||
                        lowerPrompt.contains("count") || lowerPrompt.contains("多少行") ||
                        lowerPrompt.contains("几条")) {
                        
                        // 尝试从 prompt 中提取表名
                        Pattern tablePattern = Pattern.compile("FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
                        Matcher tableMatcher = tablePattern.matcher(prompt);
                        if (tableMatcher.find()) {
                            String tableName = tableMatcher.group(1);
                            String sql = "SELECT COUNT(*) AS total FROM " + tableName;
                            log.info("Fallback 生成统计 SQL: {}", sql);
                            return sql;
                        }
                        
                        // 如果没找到 FROM 子句，尝试从用户查询中提取常见表名
                        if (lowerPrompt.contains("chatinfo") || lowerPrompt.contains("chat_info") || lowerPrompt.contains("chat info")) {
                            String sql = "SELECT COUNT(*) AS total FROM chatInfo";
                            log.info("Fallback 生成 chatInfo 统计 SQL: {}", sql);
                            return sql;
                        }
                        // 其他常见表名
                        if (lowerPrompt.contains("user") && !lowerPrompt.contains("users")) {
                            String sql = "SELECT COUNT(*) AS total FROM users";
                            log.info("Fallback 生成 users 统计 SQL: {}", sql);
                            return sql;
                        }
                        if (lowerPrompt.contains("order")) {
                            String sql = "SELECT COUNT(*) AS total FROM orders";
                            log.info("Fallback 生成 orders 统计 SQL: {}", sql);
                            return sql;
                        }
                        if (lowerPrompt.contains("product")) {
                            String sql = "SELECT COUNT(*) AS total FROM products";
                            log.info("Fallback 生成 products 统计 SQL: {}", sql);
                            return sql;
                        }
                    }
                    
                    // 检测是否是查询所有数据
                    if (lowerPrompt.contains("查询所有") || lowerPrompt.contains("查询全部") || 
                        lowerPrompt.contains("select all") || lowerPrompt.contains("所有数据")) {
                        
                        Pattern tablePattern = Pattern.compile("FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
                        Matcher tableMatcher = tablePattern.matcher(prompt);
                        if (tableMatcher.find()) {
                            String tableName = tableMatcher.group(1);
                            String sql = "SELECT * FROM " + tableName + " LIMIT 100";
                            log.info("Fallback 生成查询所有 SQL: {}", sql);
                            return sql;
                        }
                    }
                    
                    // 检测是否是查询特定记录
                    if (lowerPrompt.contains("查询") && lowerPrompt.contains("等于")) {
                        // 尝试提取列名和值
                        Pattern colValPattern = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*'?([^'\"\\s]+)'?", Pattern.CASE_INSENSITIVE);
                        Matcher colValMatcher = colValPattern.matcher(prompt);
                        if (colValMatcher.find()) {
                            String columnName = colValMatcher.group(1);
                            String value = colValMatcher.group(2);
                            
                            Pattern tablePattern = Pattern.compile("FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
                            Matcher tableMatcher = tablePattern.matcher(prompt);
                            if (tableMatcher.find()) {
                                String tableName = tableMatcher.group(1);
                                String sql = "SELECT * FROM " + tableName + " WHERE " + columnName + " = '" + value + "' LIMIT 100";
                                log.info("Fallback 生成条件查询 SQL: {}", sql);
                                return sql;
                            }
                        }
                    }
                    
                    // 最后兜底：返回最简单的 SELECT
                    // 但必须包含 FROM 子句，否则 queryForList 会失败
                    log.warn("无法识别查询意图，返回兜底 SQL");
                    return "SELECT COUNT(*) AS total FROM chatInfo";
                    
                } catch (Exception e) {
                    log.warn("Fallback SQL 生成失败，使用兜底方案", e);
                    return "SELECT COUNT(*) AS total FROM chatInfo";
                }
            }

            @Override
            public String doSQL(String question) {
                log.info("使用 Fallback 的 doSQL 方法");
                return "抱歉，SQL 执行服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }
}
