package org.fb.bean.mcpbean;

public class McpMessage {
    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private Object params;
    private Object result;
    private Object error;

    public static McpMessage request(String id, String method, Object params) {
        McpMessage msg = new McpMessage();
        msg.id = id;
        msg.method = method;
        msg.params = params;
        return msg;
    }

    public static McpMessage response(String id, Object result) {
        McpMessage msg = new McpMessage();
        msg.id = id;
        msg.result = result;
        return msg;
    }

    public static McpMessage notification(String method, Object params) {
        McpMessage msg = new McpMessage();
        msg.method = method;
        msg.params = params;
        return msg;
    }

    public static McpMessage error(String id, int code, String message) {
        McpMessage msg = new McpMessage();
        msg.id = id;
        msg.error = new McpError(code, message);
        return msg;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
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

    public static class McpError {
        private int code;
        private String message;

        public McpError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
