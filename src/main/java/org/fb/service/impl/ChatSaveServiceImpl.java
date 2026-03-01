package org.fb.service.impl;

import org.fb.constant.BusinessConstant;
import org.fb.service.ChatSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 聊天记录保存服务实现
 * 负责将聊天记录保存到MySQL数据库
 * */
@Service
public class ChatSaveServiceImpl implements ChatSaveService {

    private static final Logger log = LoggerFactory.getLogger(ChatSaveServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void saveChatInfo(Long memoryId, String userMessage, String chatType, String aiResponse) {
        log.info("\n=== 开始保存聊天信息 ===");
        log.info("memoryId: {}, chatType: {}", memoryId, chatType);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            String sql = "INSERT INTO chatInfo (chat_memory_id, chat_info, chat_type, create_time) VALUES (?, ?, ?, ?)";
            log.info("执行SQL: {}", sql);

            int result = jdbcTemplate.update(sql, String.valueOf(memoryId), userMessage, chatType, currentTime);

            log.info("JdbcTemplate.update返回结果: {}", result);
            log.info("聊天信息保存成功, memoryId: {}, chatType: {}", memoryId, chatType);
            log.info("=== 聊天信息保存完成 ===\n");
        } catch (Exception e) {
            log.error("\n=== 保存聊天信息失败 ===");
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("异常栈: ", e);
            log.error("=== 保存聊天信息失败完成 ===\n");
        }
    }

    @Override
    public void saveChatInfo(Long memoryId, String userMessage, String chatType) {
        saveChatInfo(memoryId, userMessage, chatType, null);
    }

    @Override
    public int deleteChatInfoByMemoryId(Long memoryId) {
        log.info("\n=== 开始删除聊天记录(按memoryId) ===");
        log.info("memoryId: {}", memoryId);

        try {
            String sql = "DELETE FROM chatInfo WHERE chat_memory_id = ?";
            log.info("执行SQL: {}", sql);

            int result = jdbcTemplate.update(sql, String.valueOf(memoryId));

            log.info("JdbcTemplate.update返回结果: {}", result);
            log.info("聊天记录删除成功, memoryId: {}, 删除数量: {}", memoryId, result);
            log.info("=== 聊天记录删除完成 ===\n");
            return result;
        } catch (Exception e) {
            log.error("\n=== 删除聊天记录失败 ===");
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("异常栈: ", e);
            log.error("=== 删除聊天记录失败完成 ===\n");
            return 0;
        }
    }

    @Override
    public int deleteChatInfoByMemoryIdAndType(Long memoryId, String chatType) {
        log.info("\n=== 开始删除聊天记录 ===");
        log.info("memoryId: {}, chatType: {}", memoryId, chatType);

        try {
            String sql = "DELETE FROM chatInfo WHERE chat_memory_id = ? AND chat_type = ?";
            log.info("执行SQL: {}", sql);

            int result = jdbcTemplate.update(sql, String.valueOf(memoryId), chatType);

            log.info("JdbcTemplate.update返回结果: {}", result);
            log.info("聊天记录删除成功, memoryId: {}, chatType: {}, 删除数量: {}", memoryId, chatType, result);
            log.info("=== 聊天记录删除完成 ===\n");
            return result;
        } catch (Exception e) {
            log.error("\n=== 删除聊天记录失败 ===");
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常消息: {}", e.getMessage());
            log.error("异常栈: ", e);
            log.error("=== 删除聊天记录失败完成 ===\n");
            return 0;
        }
    }
}
