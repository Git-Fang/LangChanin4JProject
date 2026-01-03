package org.fb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.fb.service.assistant.BaiduMapMcpAssistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;


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
    private BaiduMapMcpAssistant baiduMapMcpAssistant;

    @GetMapping(value = "/chat0")
    @Operation(summary = "MCP助手0--默认百度地图")
    public Flux<String> defaultChat(String prompt) throws IOException {

        return baiduMapMcpAssistant.chat(prompt);
    }


}
