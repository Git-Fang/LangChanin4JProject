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
