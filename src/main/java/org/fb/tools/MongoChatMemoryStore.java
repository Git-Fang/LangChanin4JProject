package org.fb.tools;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.fb.bean.ChatMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 使用自定义的消息序列化器确保与OpenAI API兼容
 */
@Component
public class MongoChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MongoChatMemoryStore.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String memoryIdStr = convertMemoryIdToString(memoryId);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);
        ChatMessages one = mongoTemplate.findOne(query, ChatMessages.class);

        if (one == null) {
            return new LinkedList<>();
        }

        try {
            // 尝试使用新的自定义序列化器
            String content = one.getContent();
            if (content == null || content.trim().isEmpty()) {
                return new LinkedList<>();
            }

            // 使用自定义序列化器进行反序列化
            // 这种格式确保content是字符串而不是对象
            List<ChatMessage> messages = ChatMessageSerializerFix.messagesFromJson(content);

            if (messages.isEmpty()) {
                // 如果自定义序列化器返回空，尝试使用原始方法
                log.debug("自定义序列化器返回空列表，尝试使用LangChain4j原生方法");
                messages = ChatMessageDeserializer.messagesFromJson(content);
            }

            return messages;
        } catch (Exception e) {
            log.warn("使用自定义序列化器失败，回退到LangChain4j原生方法: {}", e.getMessage());
            try {
                return ChatMessageDeserializer.messagesFromJson(one.getContent());
            } catch (Exception ex) {
                log.error("两种序列化方法都失败，返回空消息列表", ex);
                return new LinkedList<>();
            }
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String memoryIdStr = convertMemoryIdToString(memoryId);
        Criteria criteria = Criteria.where("memoryId").is(memoryIdStr);
        Query query = new Query(criteria);

        Update update = new Update();
        // 使用自定义序列化器确保格式兼容
        String contentJson = ChatMessageSerializerFix.messagesToJson(list);
        update.set("content", contentJson);

        log.debug("保存消息到MongoDB: memoryId={}, 消息数量={}, 序列化长度={}",
                  memoryIdStr, list.size(), contentJson.length());

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
                    .filter(chatMessages -> chatMessages.getContent() != null && !chatMessages.getContent().trim().isEmpty())
                    .map(ChatMessages::getMemoryId)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
