package org.fb.util;

import com.fasterxml.jackson.core.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonParseErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonParseErrorHandler.class);

    private static final Pattern INVALID_UNICODE_ESCAPE_PATTERN = Pattern.compile(
        "\\\\u([0-9a-fA-F]{0,3})[^\u4e00-\u9fff]"
    );

    private static final Pattern TRUNCATED_UNICODE_PATTERN = Pattern.compile(
        "\\\\u([0-9a-fA-F]{1,3})$"
    );

    public static class CleanJsonResult {
        public final String cleanedJson;
        public final boolean wasCleaned;
        public final String originalError;

        public CleanJsonResult(String cleanedJson, boolean wasCleaned, String originalError) {
            this.cleanedJson = cleanedJson;
            this.wasCleaned = wasCleaned;
            this.originalError = originalError;
        }
    }

    public static CleanJsonResult cleanInvalidUnicodeEscapes(String jsonInput) {
        if (jsonInput == null || jsonInput.isEmpty()) {
            return new CleanJsonResult(jsonInput, false, null);
        }

        try {
            String cleaned = jsonInput;

            Matcher truncatedMatcher = TRUNCATED_UNICODE_PATTERN.matcher(cleaned);
            if (truncatedMatcher.find()) {
                String matched = truncatedMatcher.group(1);
                log.warn("检测到截断的Unicode转义序列: \\u{}, 进行清理", matched);
                cleaned = truncatedMatcher.replaceAll("?");
            }

            Matcher invalidMatcher = INVALID_UNICODE_ESCAPE_PATTERN.matcher(cleaned);
            if (invalidMatcher.find()) {
                String matched = invalidMatcher.group(0);
                log.warn("检测到无效的Unicode转义序列: {}, 进行清理", matched);
                cleaned = invalidMatcher.replaceAll("?");
            }

            boolean wasCleaned = !cleaned.equals(jsonInput);
            if (wasCleaned) {
                log.info("JSON已清理，去除无效的Unicode转义序列");
            }

            return new CleanJsonResult(cleaned, wasCleaned, null);
        } catch (Exception e) {
            log.error("清理JSON时发生错误: {}", e.getMessage());
            return new CleanJsonResult(jsonInput, false, e.getMessage());
        }
    }

    public static String handleJsonParseException(JsonParseException ex) {
        String originalMessage = ex.getMessage();
        log.error("JSON解析错误: {}", originalMessage);

        StringBuilder userFriendlyMessage = new StringBuilder();
        userFriendlyMessage.append("抱歉，处理您的医疗咨询时出现错误。\n\n");

        if (originalMessage != null && originalMessage.contains("Unexpected character")) {
            if (originalMessage.contains("顼") || originalMessage.contains("0x987c")) {
                userFriendlyMessage.append("检测到特殊字符编码问题。\n\n");
                userFriendlyMessage.append("建议：\n");
                userFriendlyMessage.append("1. 请尝试重新描述您的问题\n");
                userFriendlyMessage.append("2. 避免使用特殊符号或非标准字符\n");
                userFriendlyMessage.append("3. 如果问题持续，请联系管理员\n\n");
            } else if (originalMessage.contains("expected a hex-digit")) {
                userFriendlyMessage.append("检测到无效的字符转义序列。\n\n");
                userFriendlyMessage.append("这可能是由于：\n");
                userFriendlyMessage.append("- AI服务返回的数据格式异常\n");
                userFriendlyMessage.append("- 网络传输过程中的编码问题\n\n");
                userFriendlyMessage.append("建议：请重新提交您的问题。如果问题持续，请联系管理员。\n");
            }
        } else {
            userFriendlyMessage.append("发生JSON解析错误。\n\n");
            userFriendlyMessage.append("建议：\n");
            userFriendlyMessage.append("1. 请重新描述您的问题\n");
            userFriendlyMessage.append("2. 简化问题描述\n");
            userFriendlyMessage.append("3. 如果问题持续，请联系管理员\n");
        }

        return userFriendlyMessage.toString();
    }

    public static byte[] cleanJsonBytes(byte[] inputBytes) {
        if (inputBytes == null) {
            return null;
        }

        try {
            String input = new String(inputBytes, StandardCharsets.UTF_8);
            CleanJsonResult result = cleanInvalidUnicodeEscapes(input);

            if (result.wasCleaned) {
                log.info("已清理JSON中的无效Unicode转义序列");
                return result.cleanedJson.getBytes(StandardCharsets.UTF_8);
            }

            return inputBytes;
        } catch (Exception e) {
            log.error("清理JSON字节时发生错误: {}", e.getMessage());
            return inputBytes;
        }
    }
}
