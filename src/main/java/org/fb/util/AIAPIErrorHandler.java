package org.fb.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI API错误处理器
 * 解析和处理来自AI模型的特定错误，提供友好的错误信息
 */
public class AIAPIErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(AIAPIErrorHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 错误模式匹配
    private static final Pattern LENGTH_PATTERN = Pattern.compile(
        "Range of input length should be \\[(\\d+),\\s*(\\d+)\\]"
    );
    private static final Pattern RATE_LIMIT_PATTERN = Pattern.compile(
        "(rate.*limit|too.*many.*requests|429)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
        "(timeout|deadline|504|connection.*timed.*out)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AUTH_PATTERN = Pattern.compile(
        "(api.*key|unauthorized|authentication|401|403)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INVALID_PARAM_PATTERN = Pattern.compile(
        "(invalid.*parameter|invalid.*request|400)",
        Pattern.CASE_INSENSITIVE
    );

    // 错误类型枚举
    public enum ErrorType {
        INPUT_TOO_LONG("输入过长", "您的问题包含的内容过多，请尝试简化或分多次提问"),
        INPUT_TOO_SHORT("输入过短", "输入内容过短，请提供更详细的问题描述"),
        RATE_LIMIT("请求过于频繁", "系统繁忙，请稍后再试"),
        TIMEOUT("请求超时", "AI服务响应超时，请稍后重试"),
        AUTH_ERROR("认证错误", "AI服务配置异常，请联系管理员"),
        INVALID_REQUEST("无效请求", "请求参数有误，请尝试重新表述问题"),
        UNKNOWN_ERROR("未知错误", "发生未知错误，请稍后重试"),
        NETWORK_ERROR("网络错误", "网络连接异常，请检查网络连接后重试");

        private final String title;
        private final String suggestion;

        ErrorType(String title, String suggestion) {
            this.title = title;
            this.suggestion = suggestion;
        }

        public String getTitle() {
            return title;
        }

        public String getSuggestion() {
            return suggestion;
        }
    }

    /**
     * 解析AI API错误响应
     * @param errorResponse 错误响应字符串
     * @return 解析后的错误信息
     */
    public static AIErrorResult parseError(String errorResponse) {
        if (errorResponse == null || errorResponse.trim().isEmpty()) {
            return new AIErrorResult(
                ErrorType.UNKNOWN_ERROR,
                "AI服务返回空响应",
                "请稍后重试，如果问题持续存在，请联系管理员"
            );
        }

        log.debug("解析AI错误响应: {}", errorResponse);

        // 尝试解析JSON格式的错误响应
        try {
            JsonNode root = objectMapper.readTree(errorResponse);
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                String message = errorNode.has("message") ?
                    errorNode.get("message").asText() : errorResponse;
                String code = errorNode.has("code") ?
                    errorNode.get("code").asText() : "";

                return parseErrorMessage(message, code);
            }
        } catch (Exception e) {
            log.debug("非JSON格式错误响应，尝试文本模式匹配");
        }

        // 文本模式匹配
        return parseErrorMessage(errorResponse, "");
    }

    /**
     * 解析错误消息
     * @param message 错误消息
     * @param code 错误代码
     * @return 解析后的错误信息
     */
    private static AIErrorResult parseErrorMessage(String message, String code) {
        if (message == null) {
            return new AIErrorResult(
                ErrorType.UNKNOWN_ERROR,
                "AI服务返回空错误消息",
                "请稍后重试"
            );
        }

        String lowerMessage = message.toLowerCase();

        // 检查输入长度错误
        Matcher lengthMatcher = LENGTH_PATTERN.matcher(message);
        if (lengthMatcher.find()) {
            int min = Integer.parseInt(lengthMatcher.group(1));
            int max = Integer.parseInt(lengthMatcher.group(2));
            return new AIErrorResult(
                ErrorType.INPUT_TOO_LONG,
                String.format("AI模型输入长度限制为%d字符，您的内容过长", max),
                "请简化您的问题描述，聚焦于核心查询需求。" +
                "例如：不要描述完整的业务场景，直接说明要查询的数据和条件。"
            );
        }

        // 检查速率限制
        if (RATE_LIMIT_PATTERN.matcher(lowerMessage).find()) {
            return new AIErrorResult(
                ErrorType.RATE_LIMIT,
                "AI服务请求过于频繁",
                "系统繁忙，请等待几秒后重试。您也可以尝试简化问题。"
            );
        }

        // 检查超时
        if (TIMEOUT_PATTERN.matcher(lowerMessage).find()) {
            return new AIErrorResult(
                ErrorType.TIMEOUT,
                "AI服务响应超时",
                "请稍后重试。如果问题持续，请尝试简化查询条件。"
            );
        }

        // 检查认证错误
        if (AUTH_PATTERN.matcher(lowerMessage).find()) {
            return new AIErrorResult(
                ErrorType.AUTH_ERROR,
                "AI服务认证失败",
                "系统配置异常，请联系管理员检查API密钥配置"
            );
        }

        // 检查无效请求
        if (INVALID_PARAM_PATTERN.matcher(lowerMessage).find() ||
            code.contains("invalid_parameter")) {
            return new AIErrorResult(
                ErrorType.INVALID_REQUEST,
                "AI服务请求参数无效",
                "请尝试重新表述您的问题，避免使用特殊字符或过长的描述"
            );
        }

        // 默认未知错误
        log.warn("未识别的AI错误类型: {}", message);
        return new AIErrorResult(
            ErrorType.UNKNOWN_ERROR,
            "AI服务返回错误",
            "请稍后重试，如果问题持续存在，请联系管理员。错误详情: " + truncateMessage(message)
        );
    }

    /**
     * 生成用户友好的错误消息
     * @param result 解析后的错误结果
     * @return 用户友好的错误消息
     */
    public static String generateUserFriendlyMessage(AIErrorResult result) {
        StringBuilder sb = new StringBuilder();

        switch (result.getErrorType()) {
            case INPUT_TOO_LONG:
                sb.append("抱歉，您的问题太长了，AI模型无法处理。\n\n");
                sb.append("建议：\n");
                sb.append("1. 简化问题描述，去除不必要的背景信息\n");
                sb.append("2. 直接说明要查询的数据和条件\n");
                sb.append("3. 例如：与其说\"我想查询2024年所有预约了张医生的患者的详细信息\"，");
                sb.append("不如说\"查询预约医生为张三的记录\"\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;

            case RATE_LIMIT:
                sb.append("抱歉，系统繁忙，请稍后再试。\n\n");
                sb.append("建议：\n");
                sb.append("1. 等待几秒后重新提交\n");
                sb.append("2. 如果频繁出现此问题，请联系管理员\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;

            case TIMEOUT:
                sb.append("抱歉，AI服务响应超时。\n\n");
                sb.append("建议：\n");
                sb.append("1. 请稍后重试\n");
                sb.append("2. 尝试简化查询条件\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;

            case AUTH_ERROR:
                sb.append("抱歉，AI服务配置异常。\n\n");
                sb.append("请联系系统管理员检查API配置。\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;

            case INVALID_REQUEST:
                sb.append("抱歉，您的问题格式有误，AI无法理解。\n\n");
                sb.append("建议：\n");
                sb.append("1. 尝试用不同的方式表述问题\n");
                sb.append("2. 避免使用特殊字符或过长的描述\n");
                sb.append("3. 直接说明要查询的数据和条件\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;

            default:
                sb.append("抱歉，AI服务暂时不可用。\n\n");
                sb.append("建议：\n");
                sb.append("1. 请稍后重试\n");
                sb.append("2. 如果问题持续，请联系管理员\n\n");
                sb.append("技术原因：").append(result.getTechnicalDetail());
                break;
        }

        return sb.toString();
    }

    /**
     * 截断错误消息，避免过长
     * @param message 原始消息
     * @return 截断后的消息
     */
    private static String truncateMessage(String message) {
        if (message == null) {
            return "null";
        }
        if (message.length() <= 200) {
            return message;
        }
        return message.substring(0, 200) + "...";
    }

    /**
     * 处理JSON解析异常
     * @param ex 异常
     * @return 解析后的错误信息
     */
    public static AIErrorResult handleJsonParseException(Throwable ex) {
        String message = ex.getMessage();
        log.error("JSON解析异常: {}", message);

        if ((message != null && message.contains("顼")) || (message != null && message.contains("0x987c"))) {
            return new AIErrorResult(
                ErrorType.INVALID_REQUEST,
                "JSON解析错误：检测到无效的Unicode转义序列（字符 '顼'）",
                "这可能是由于AI服务返回的数据格式异常或网络传输过程中的编码问题导致。\n" +
                "建议：\n" +
                "1. 请重新描述您的问题\n" +
                "2. 避免使用特殊符号或非标准字符\n" +
                "3. 如果问题持续，请联系管理员"
            );
        } else if (message != null && message.contains("expected a hex-digit")) {
            return new AIErrorResult(
                ErrorType.INVALID_REQUEST,
                "JSON解析错误：无效的字符转义序列",
                "AI服务返回的数据格式异常。\n" +
                "建议：\n" +
                "1. 请重新提交您的问题\n" +
                "2. 如果问题持续，请联系管理员"
            );
        } else {
            return new AIErrorResult(
                ErrorType.INVALID_REQUEST,
                "JSON解析错误: " + truncateMessage(message),
                "系统无法解析AI服务的响应数据。\n" +
                "建议：\n" +
                "1. 请重新提交您的问题\n" +
                "2. 如果问题持续，请联系管理员"
            );
        }
    }

    /**
     * 判断错误是否应该重试
     * @param result 错误结果
     * @return 是否可重试
     */
    public static boolean isRetryable(AIErrorResult result) {
        switch (result.getErrorType()) {
            case RATE_LIMIT:
            case TIMEOUT:
            case NETWORK_ERROR:
                return true;
            case INPUT_TOO_LONG:
            case INPUT_TOO_SHORT:
            case AUTH_ERROR:
            case INVALID_REQUEST:
                return false;
            case UNKNOWN_ERROR:
            default:
                return true;
        }
    }

    /**
     * AI错误结果封装类
     */
    public static class AIErrorResult {
        private final ErrorType errorType;
        private final String technicalDetail;
        private final String userSuggestion;

        public AIErrorResult(ErrorType errorType, String technicalDetail, String userSuggestion) {
            this.errorType = errorType;
            this.technicalDetail = technicalDetail;
            this.userSuggestion = userSuggestion;
        }

        public ErrorType getErrorType() {
            return errorType;
        }

        public String getTechnicalDetail() {
            return technicalDetail;
        }

        public String getUserSuggestion() {
            return userSuggestion;
        }

        @Override
        public String toString() {
            return "AIErrorResult{" +
                   "errorType=" + errorType +
                   ", technicalDetail='" + truncateMessage(technicalDetail) + '\'' +
                   '}';
        }
    }
}
