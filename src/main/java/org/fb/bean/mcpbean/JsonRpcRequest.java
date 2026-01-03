package org.fb.bean.mcpbean;

import lombok.Data;

@Data
public class JsonRpcRequest {
    private String jsonrpc = "2.0";

    private String method;     // 工具名

    private Object params;     // 参数（Map 或 JsonNode）

    private String id;

}