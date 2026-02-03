package org.fb.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fb.context.ModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 模型选择拦截器
 * 从请求头中读取用户选择的大模型，并设置到ModelContext中
 */
@Component
public class ModelInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ModelInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头中获取模型选择
        String selectedModel = request.getHeader(ModelContext.MODEL_HEADER);

        if (selectedModel != null && !selectedModel.trim().isEmpty()) {
            log.debug("拦截器从请求头获取模型选择: {}", selectedModel);
            ModelContext.setModel(selectedModel.trim());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求完成后清理ThreadLocal
        ModelContext.clearModel();
    }
}
