package org.fb.service;

/**
 * 聊天记录保存服务接口
 * 提供统一的数据库保存逻辑
 * */
public interface ChatSaveService {
    
    /**
     * 保存聊天信息到数据库
     * @param memoryId 对话对应的memoryId
     * @param userMessage 用户消息
     * @param chatType 聊天类型
     * @param aiResponse AI回复内容
     */
    void saveChatInfo(Long memoryId, String userMessage, String chatType, String aiResponse);
    
    /**
     * 保存聊天信息到数据库（无AI回复内容）
     * @param memoryId 对话对应的memoryId
     * @param userMessage 用户消息
     * @param chatType 聊天类型
     */
    void saveChatInfo(Long memoryId, String userMessage, String chatType);

    /**
     * 删除指定memoryId和chatType的聊天记录
     * 用于清理默认的general类型记录，保留意图匹配的类型
     * @param memoryId 对话对应的memoryId
     * @param chatType 聊天类型
     * @return 删除的记录数量
     */
    int deleteChatInfoByMemoryIdAndType(Long memoryId, String chatType);
}
