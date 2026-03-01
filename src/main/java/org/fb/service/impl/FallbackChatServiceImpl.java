package org.fb.service.impl;

import org.fb.service.ChatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * 当没有配置LLM模型时的 ChatService fallback 实现
 * 提供基本功能以确保应用程序能够启动
 */
@Service
@ConditionalOnMissingBean(ChatService.class)
public class FallbackChatServiceImpl implements ChatService {

    @Override
    public String chat(Long memoryId, String message) {
        return "抱歉，聊天服务暂时不可用，请配置 LLM 模型后重试。";
    }
}
