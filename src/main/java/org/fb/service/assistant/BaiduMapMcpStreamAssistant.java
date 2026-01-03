package org.fb.service.assistant;

import reactor.core.publisher.Flux;

/**
 * 流式百度地图mcp server
 * */
public interface BaiduMapMcpStreamAssistant {

    Flux<String> chat(String userMessage);
}
