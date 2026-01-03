package org.fb.service.assistant;

import reactor.core.publisher.Flux;

public interface McpAssistant {
    Flux<String> chat(String userMessage);
}
