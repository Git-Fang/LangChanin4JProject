package org.fb.bean.mcpbean;

import java.util.Map;

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

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Object getError() {
        return error;
    }

    public void setError(Object error) {
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}