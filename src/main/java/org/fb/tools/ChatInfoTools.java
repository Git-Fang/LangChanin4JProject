package org.fb.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import mapper.ChatInfoMapper;
import org.fb.bean.ChatInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatInfoTools {

    @Autowired
    private ChatInfoMapper chatInfoMapper;

    @Tool(name="对话记录保存", value = "将对话相关数据保存写入数据库ChatInfo表")
    public void saveChatInfo(ChatInfo chatInfo){

        chatInfoMapper.insert(chatInfo);

        log.info("保存对话数据成功：{}",chatInfo);
    }

}
