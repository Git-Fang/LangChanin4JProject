package org.fb.bean;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("chat_messages")  // 用于mongodb的集合映射
public class ChatMessages {

    //唯一标识，映射到 MongoDB 文档的 _id 字段
    @Id
    private ObjectId id;

    private long memoryId;

    private String content;

    // 无参构造函数
    public ChatMessages() {
    }

    // 全参构造函数
    public ChatMessages(ObjectId id, long memoryId, String content) {
        this.id = id;
        this.memoryId = memoryId;
        this.content = content;
    }

    // Getter 和 Setter 方法
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(long memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
