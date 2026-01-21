package org.fb.config;

import dev.langchain4j.exception.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleException(Exception e, WebRequest request) {
        log.error("请求处理异常", e);
        
        // 排除SSE请求，避免与SSE流冲突
        if (isSseRequest(request)) {
            return null;
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal Server Error",
                        "message", "处理请求时发生错误，请稍后重试"
                ));
    }

    @ExceptionHandler(HttpException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<Map<String, Object>> handleHttpException(HttpException e, WebRequest request) {
        log.error("AI服务认证失败: {}", e.getMessage());
        
        // 排除SSE请求
        if (isSseRequest(request)) {
            return null;
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "Authentication Failed",
                        "message", "AI服务认证失败，请检查API Key配置"
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, WebRequest request) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        
        // 排除SSE请求
        if (isSseRequest(request)) {
            return null;
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Bad Request",
                        "message", "参数类型不匹配: " + e.getName()
                ));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException e, WebRequest request) {
        log.warn("未找到处理器: {} {}", e.getHttpMethod(), e.getRequestURL());
        
        // 排除SSE请求
        if (isSseRequest(request)) {
            return null;
        }
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Not Found",
                        "message", "请求的资源不存在"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        log.warn("参数错误: {}", e.getMessage());
        
        // 排除SSE请求
        if (isSseRequest(request)) {
            return null;
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Bad Request",
                        "message", e.getMessage()
                ));
    }
    
    private boolean isSseRequest(WebRequest request) {
        String description = request.getDescription(false);
        if (description == null) {
            return false;
        }
        return description.contains("uri=/xiaozhi/chat/stream") ||
               description.contains("uri=/xiaozhi/chat/streaming") ||
               description.contains("uri=/xiaozhi/chat/http-stream");
    }
}
