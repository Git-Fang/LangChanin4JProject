package org.fb.controller;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.assistant.McpAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * 在调用接口之前，注意使用npx或python指令启动各mcp server服务。
 * # 对于 bing-search-mcp 服务
 * npx -y @smithery/cli@latest run @leehanchung/bing-search-mcp --key 653d496e-5ac2-422b-96c8-a563ad35cda0 --profile related-toucan-1vlWpf
 *
 * # 对于百度地图服务
 * npx -y @baidumap/mcp-server-baidu-map
 *
 * # 对于微信服务
 * python -m mcp_server_wechat --folder-path=F:\xwechat_files
 * */
@RestController
@RequestMapping("/ragTranslation/mcp")
@Slf4j
@Tag(name = "MCP助手")
public class McpChatController {

    @Autowired
    private StreamingChatModel streamingChatModel;

    @GetMapping(value = "/chat0")
    @Operation(summary = "MCP助手0--默认百度地图")
    public Flux<String> defaultChat(String prompt) throws IOException {

        // 1.确定调用的mcp server
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of("cmd", "/c", "npx", "-y", "@baidumap/mcp-server-baidu-map"))
                .environment(Map.of("BAIDU_MAP_API_KEY", "8qM3bsI6oakw1ICy1g1T9Vo0peSP90of"))
                .logEvents(true) // only if you want to see the traffic in the log
                .build();

        // 2.初始化MCP client、实例MCP工具提供者对象
        McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();

        McpToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();

        // 3.将chatModel和工具提供者注入MCP助手
        McpAssistant mcpAssistant = AiServices.builder(McpAssistant.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(toolProvider)
                .build();

        // 4.业务调用
        return mcpAssistant.chat(prompt);
    }


}
