package org.fb.tools;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.fb.bean.ChatMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;


/**
 * 实现mongodb等结构化数据库底层接口：通过MongoTemplate操作
 * */
@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String memoryIdStr = convertMemoryIdToString(memoryId);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);
        ChatMessages one = mongoTemplate.findOne(query, ChatMessages.class);
        if(one==null) return new LinkedList<>();

        return ChatMessageDeserializer.messagesFromJson(one.getContent());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String memoryIdStr = convertMemoryIdToString(memoryId);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);

        Update update = new Update();
        update.set("content", ChatMessageSerializer.messagesToJson(list));
        mongoTemplate.upsert(query, update, ChatMessages.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String memoryIdStr = convertMemoryIdToString(memoryId);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);

        mongoTemplate.remove(query, ChatMessages.class);
    }

    /**
     * 将memoryId转换为String类型
     * @param memoryId memoryId对象（可能是Long或String）
     * @return String类型的memoryId
     */
    private String convertMemoryIdToString(Object memoryId) {
        if (memoryId == null) {
            return "default";
        }
        if (memoryId instanceof String) {
            return (String) memoryId;
        }
        return String.valueOf(memoryId);
    }

    /**
     * 获取所有历史会话的memoryId
     * @return 所有历史会话的memoryId列表
     */
    public List<String> getAllMemoryIds() {
        try {
            return mongoTemplate.find(new Query(), ChatMessages.class)
                    .stream()
                    .map(ChatMessages::getMemoryId)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
