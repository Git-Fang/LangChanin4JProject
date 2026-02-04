package org.fb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonParseErrorHandlerTest {

    @Test
    public void testCleanInvalidUnicodeEscapes_WithTruncatedUnicode() {
        String input = "{\"name\":\"测试\\u987\"}";
        JsonParseErrorHandler.CleanJsonResult result = JsonParseErrorHandler.cleanInvalidUnicodeEscapes(input);

        assertTrue(result.wasCleaned);
        assertNotNull(result.cleanedJson);
    }

    @Test
    public void testCleanInvalidUnicodeEscapes_WithValidJson() {
        String input = "{\"name\":\"测试\",\"value\":123}";
        JsonParseErrorHandler.CleanJsonResult result = JsonParseErrorHandler.cleanInvalidUnicodeEscapes(input);

        assertFalse(result.wasCleaned);
        assertEquals(input, result.cleanedJson);
    }

    @Test
    public void testCleanInvalidUnicodeEscapes_WithNullInput() {
        JsonParseErrorHandler.CleanJsonResult result = JsonParseErrorHandler.cleanInvalidUnicodeEscapes(null);

        assertFalse(result.wasCleaned);
        assertNull(result.cleanedJson);
    }

    @Test
    public void testCleanInvalidUnicodeEscapes_WithEmptyInput() {
        JsonParseErrorHandler.CleanJsonResult result = JsonParseErrorHandler.cleanInvalidUnicodeEscapes("");

        assertFalse(result.wasCleaned);
        assertEquals("", result.cleanedJson);
    }

    @Test
    public void testHandleJsonParseException_WithSpecialChar() {
        String errorMessage = "Unexpected character ('顼' (code 39036 / 0x987c)): expected a hex-digit";
        AIAPIErrorHandler.AIErrorResult result = AIAPIErrorHandler.handleJsonParseException(
            new RuntimeException(errorMessage)
        );

        assertNotNull(result);
        assertEquals(AIAPIErrorHandler.ErrorType.INVALID_REQUEST, result.getErrorType());
    }

    @Test
    public void testHandleJsonParseException_WithHexDigitError() {
        String errorMessage = "expected a hex-digit for character escape sequence";
        AIAPIErrorHandler.AIErrorResult result = AIAPIErrorHandler.handleJsonParseException(
            new RuntimeException(errorMessage)
        );

        assertNotNull(result);
        assertEquals(AIAPIErrorHandler.ErrorType.INVALID_REQUEST, result.getErrorType());
    }

    @Test
    public void testCleanJsonBytes() {
        String input = "{\"name\":\"测试\\u987\"}";
        byte[] inputBytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] result = JsonParseErrorHandler.cleanJsonBytes(inputBytes);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    public void testCleanJsonBytes_WithNullInput() {
        byte[] result = JsonParseErrorHandler.cleanJsonBytes(null);

        assertNull(result);
    }
}
