package org.fb.config.toolsConfig;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class MongoDBTools {

    @Autowired
    private MongoChatMemoryStore mongoChatMemoryStore;


    @Tool(name = "查询mongoDB数据库信息", value="根据用户输入信息从mongoDB数据库中查询后，并返回给用户")
    public String embeddingSearch(@P(value="memoryId", required = true) Object memoryId) {
        List<ChatMessage> messages = mongoChatMemoryStore.getMessages(memoryId);

        if(CollectionUtils.isEmpty(messages)){
            return "暂未找到相关信息";
        } else {
            ChatMessage chatMessage = messages.get(0);
            return chatMessage.toString();
        }
    }



}
