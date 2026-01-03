package org.fb.service.assistant;

import reactor.core.publisher.Flux;

public interface BaiduMapMcpAssistant {

    Flux<String> chat(String userMessage);
}
