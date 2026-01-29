package org.fb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器，处理Redis连接失败等异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理Redis连接失败异常
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, Object>> handleRedisConnectionFailure(
            RedisConnectionFailureException ex, WebRequest request) {
        log.error("Redis连接失败: {}", ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Redis连接失败");
        response.put("message", "缓存服务暂时不可用，系统将继续运行");
        response.put("status", "WARNING");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        // 返回200状态码，因为这是一个警告而不是错误
        return ResponseEntity.ok(response);
    }

    /**
     * 处理通用数据访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(
            DataAccessException ex, WebRequest request) {
        log.error("数据访问异常: {}", ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "数据访问异常");
        response.put("message", "数据服务暂时不可用，系统将继续运行");
        response.put("status", "WARNING");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity.ok(response);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            Exception ex, WebRequest request) {
        log.error("发生异常: {}", ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "系统错误");
        response.put("message", ex.getMessage());
        response.put("status", "ERROR");
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
