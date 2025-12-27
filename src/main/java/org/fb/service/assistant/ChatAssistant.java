package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface ChatAssistant {
    String chat(String userMessage);

    public String chat(@MemoryId long memoryId, @UserMessage String userMessage);
}
