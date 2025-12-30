package org.fb.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ChatAssistant {
    String chat(String userMessage);

    @SystemMessage(fromResource = "default-prompt.txt")
    public String chat(@MemoryId long memoryId, @UserMessage String userMessage);
}
