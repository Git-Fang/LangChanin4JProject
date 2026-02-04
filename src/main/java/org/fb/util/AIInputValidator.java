package org.fb.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI模型输入验证器
 * 用于验证和限制AI模型输入的长度，防止超出模型限制
 */
public class AIInputValidator {

    private static final Logger log = LoggerFactory.getLogger(AIInputValidator.class);

    // 默认AI模型输入限制 (根据错误信息中的30720字符限制)
    public static final int DEFAULT_MAX_INPUT_LENGTH = 30000;
    public static final int DEFAULT_MIN_INPUT_LENGTH = 1;

    // 安全边距 (预留一些空间给模型输出)
    public static final int SAFETY_MARGIN = 1000;

    // 警告阈值 (超过此长度时记录警告)
    public static final int WARNING_THRESHOLD = 25000;

    /**
     * 验证输入是否在有效范围内
     * @param input 输入字符串
     * @param maxLength 最大长度
     * @return 验证结果
     */
    public static ValidationResult validate(String input, int maxLength) {
        if (input == null) {
            return new ValidationResult(false, "输入不能为空", null);
        }

        String trimmedInput = input.trim();
        int length = trimmedInput.length();

        if (length < DEFAULT_MIN_INPUT_LENGTH) {
            return new ValidationResult(false,
                String.format("输入长度(%d)小于最小限制(%d)", length, DEFAULT_MIN_INPUT_LENGTH),
                null);
        }

        if (length > maxLength) {
            log.warn("输入长度({})超过最大限制({})，需要截断", length, maxLength);
            String truncated = trimmedInput.substring(0, maxLength);
            return new ValidationResult(false,
                String.format("输入长度(%d)超过最大限制(%d)，已自动截断", length, maxLength),
                truncated);
        }

        if (length > WARNING_THRESHOLD) {
            log.warn("输入长度({})接近最大限制({})，建议优化输入内容", length, maxLength);
        }

        return new ValidationResult(true, "输入验证通过", null);
    }

    /**
     * 验证输入是否在有效范围内（使用默认最大长度）
     * @param input 输入字符串
     * @return 验证结果
     */
    public static ValidationResult validate(String input) {
        return validate(input, DEFAULT_MAX_INPUT_LENGTH);
    }

    /**
     * 安全截断输入到指定长度
     * @param input 输入字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }

    /**
     * 计算输入的建议最大schema长度
     * @param systemInstructions 系统指令长度
     * @param userQuery 用户查询长度
     * @param maxTotalLength 最大总长度
     * @return 建议的schema最大长度
     */
    public static int calculateMaxSchemaLength(int systemInstructions, int userQuery, int maxTotalLength) {
        int availableLength = maxTotalLength - systemInstructions - userQuery - SAFETY_MARGIN;
        return Math.max(0, availableLength);
    }

    /**
     * 智能截断schema信息，保留关键表结构
     * @param schema 完整的schema信息
     * @param maxLength 最大长度
     * @return 截断后的schema
     */
    public static String truncateSchema(String schema, int maxLength) {
        if (schema == null || schema.length() <= maxLength) {
            return schema;
        }

        // 尝试找到最后一个完整的表结构结束位置
        int lastNewline = schema.lastIndexOf('\n', maxLength);
        if (lastNewline > maxLength * 0.8) { // 确保保留了至少80%的空间
            return schema.substring(0, lastNewline) +
                   "\n\n[警告：schema信息过长，已自动截断]";
        }

        // 如果找不到合适的截断点，直接截断并添加提示
        return schema.substring(0, maxLength - 50) +
               "\n\n[警告：schema信息过长，已自动截断]";
    }

    /**
     * 智能截断用户输入，保留问题关键信息
     * 当输入过长时，优先保留问题的核心内容（如"XXX有哪些？"、"XXX是什么？"等）
     * @param input 输入字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    public static String smartTruncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }

        // 尝试找到问题的核心部分
        // 常见模式：问题核心通常在问号之后、句号之后，或者以特定关键词开头
        String trimmedInput = input.trim();

        // 策略1：查找问号，保留问号之后的内容（通常是问题的核心）
        int questionMarkIndex = trimmedInput.lastIndexOf('？');
        if (questionMarkIndex == -1) {
            questionMarkIndex = trimmedInput.lastIndexOf('?');
        }

        if (questionMarkIndex != -1 && questionMarkIndex < trimmedInput.length() - 1) {
            // 找到问号，尝试保留问号后面的核心问题
            String coreQuestion = trimmedInput.substring(questionMarkIndex + 1).trim();
            if (!coreQuestion.isEmpty() && coreQuestion.length() <= maxLength * 0.3) {
                // 核心问题较短，可以保留
                // 但如果太短，可能不完整，尝试获取更多上下文
                int startIndex = Math.max(0, questionMarkIndex - maxLength / 3);
                String truncated = trimmedInput.substring(startIndex, Math.min(startIndex + maxLength, trimmedInput.length()));
                log.info("智能截断：找到问号，保留核心问题内容");
                return truncated;
            }
        }

        // 策略2：查找常见的知识库查询关键词，保留关键词之后的内容
        String[] knowledgeKeywords = {"请结合知识库", "知识库中", "查询知识库", "从知识库", "基于知识库"};
        int earliestKeywordIndex = -1;
        for (String keyword : knowledgeKeywords) {
            int index = trimmedInput.indexOf(keyword);
            if (index != -1 && (earliestKeywordIndex == -1 || index < earliestKeywordIndex)) {
                earliestKeywordIndex = index;
            }
        }

        if (earliestKeywordIndex != -1 && earliestKeywordIndex < trimmedInput.length() - 1) {
            // 找到知识库关键词，尝试从关键词位置开始截断
            int startIndex = Math.max(0, earliestKeywordIndex - maxLength / 5);
            String truncated = trimmedInput.substring(startIndex, Math.min(startIndex + maxLength, trimmedInput.length()));
            log.info("智能截断：找到知识库关键词，从关键词位置开始截断");
            return truncated;
        }

        // 策略3：查找句号，保留最后一个句号之后的内容
        int lastPeriodIndex = Math.max(trimmedInput.lastIndexOf('。'), trimmedInput.lastIndexOf('.'));
        if (lastPeriodIndex != -1 && lastPeriodIndex < trimmedInput.length() - 1) {
            String lastSentence = trimmedInput.substring(lastPeriodIndex + 1).trim();
            if (lastSentence.length() <= maxLength * 0.4) {
                // 最后一句较短，可能是问题的核心
                int startIndex = Math.max(0, lastPeriodIndex - maxLength / 2);
                String truncated = trimmedInput.substring(startIndex, Math.min(startIndex + maxLength, trimmedInput.length()));
                log.info("智能截断：保留最后一句核心内容");
                return truncated;
            }
        }

        // 策略4：如果以上策略都不适用，直接从开头截断
        log.info("智能截断：使用默认截断策略，从开头截断");
        return trimmedInput.substring(0, maxLength);
    }

    /**
     * 智能截断用户输入（使用默认最大长度）
     * @param input 输入字符串
     * @return 截断后的字符串
     */
    public static String smartTruncate(String input) {
        return smartTruncate(input, DEFAULT_MAX_INPUT_LENGTH);
    }

    /**
      * 验证结果封装类
      */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String truncatedInput;

        public ValidationResult(boolean valid, String message, String truncatedInput) {
            this.valid = valid;
            this.message = message;
            this.truncatedInput = truncatedInput;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public String getTruncatedInput() {
            return truncatedInput;
        }

        @Override
        public String toString() {
            return "ValidationResult{" +
                   "valid=" + valid +
                   ", message='" + message + '\'' +
                   ", hasTruncatedInput=" + (truncatedInput != null) +
                   '}';
        }
    }
}
