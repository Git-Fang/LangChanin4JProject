package org.fb.bean.mcpbean;

import lombok.Data;

import java.util.Map;

@Data
public class JsonRpcResponse {

    private String jsonrpc = "2.0";

    private Object result;

    private Object error;

    private String id;

    // 静态工厂方法
    public static JsonRpcResponse success(Object result, String id) {
        JsonRpcResponse r = new JsonRpcResponse();
        r.result = result;
        r.id = id;
        return r;
    }

    public static JsonRpcResponse error(String message, String id) {
        JsonRpcResponse r = new JsonRpcResponse();
        r.error = Map.of("code", -32603, "message", message);
        r.id = id;
        return r;
    }
}