package org.fb.tools;

import dev.langchain4j.agent.tool.Tool;
import mapper.ChatInfoMapper;
import org.fb.bean.ChatInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatInfoTools {
    private static final Logger log = LoggerFactory.getLogger(ChatInfoTools.class);

    @Autowired
    private ChatInfoMapper chatInfoMapper;

    @Tool(name="save_chat_info", value = "对话记录保存:将对话相关数据保存写入数据库ChatInfo表")
    public void saveChatInfo(ChatInfo chatInfo){

        chatInfoMapper.insert(chatInfo);

        log.info("保存对话数据成功：{}",chatInfo);
    }

}
