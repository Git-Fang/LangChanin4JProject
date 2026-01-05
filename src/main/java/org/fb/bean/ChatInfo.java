package org.fb.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 
 * @TableName appointment
 */
@TableName(value ="chatInfo")
public class ChatInfo {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String chatMemoryId;

    /**
     * 
     */
    private String chatInfo;

    /**
     * 
     */
    private String chatType;

    /**
     *
     */
    private String createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChatMemoryId() {
        return chatMemoryId;
    }

    public void setChatMemoryId(String chatMemoryId) {
        this.chatMemoryId = chatMemoryId;
    }

    public String getChatInfo() {
        return chatInfo;
    }

    public void setChatInfo(String chatInfo) {
        this.chatInfo = chatInfo;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}