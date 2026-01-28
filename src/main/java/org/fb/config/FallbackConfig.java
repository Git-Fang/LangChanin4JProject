package org.fb.config;

import org.fb.service.assistant.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 当没有配置LLM模型时的 fallback 配置
 * 提供占位符 bean 以确保应用程序能够启动
 */
@Configuration
public class FallbackConfig {

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

            @Override
            public String chatWithTermTool(String userMessage) {
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
                return "抱歉，SQL 转换服务暂时不可用，请配置 LLM 模型后重试。";
            }

            @Override
            public String doSQL(String question) {
                return "抱歉，SQL 执行服务暂时不可用，请配置 LLM 模型后重试。";
            }
        };
    }
}
