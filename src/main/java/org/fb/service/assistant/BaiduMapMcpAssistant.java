package org.fb.service.assistant;

import dev.langchain4j.agent.tool.Tool;

/**
 * 非流式百度地图mcp server
 * */
public interface BaiduMapMcpAssistant {

    @Tool(name = "baidu_map_mcp", value="根据传入数据{{userMessage}}调用百度地图mcp server完成路径规划等需求")
    String chatDirectly(String userMessage);
}
