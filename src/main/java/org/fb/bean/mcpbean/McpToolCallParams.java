package org.fb.bean.mcpbean;

import java.util.Map;

public class McpToolCallParams {
    private String name;
    private Map<String, Object> arguments;

    public static McpToolCallParams of(String name, Map<String, Object> arguments) {
        McpToolCallParams params = new McpToolCallParams();
        params.name = name;
        params.arguments = arguments;
        return params;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
