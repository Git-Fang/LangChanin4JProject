package org.fb.bean.mcpbean;

import java.util.List;
import java.util.Map;

public class McpToolsResult {
    private List<Map<String, Object>> tools;

    public static McpToolsResult of(List<Map<String, Object>> tools) {
        McpToolsResult result = new McpToolsResult();
        result.tools = tools;
        return result;
    }

    public List<Map<String, Object>> getTools() {
        return tools;
    }

    public void setTools(List<Map<String, Object>> tools) {
        this.tools = tools;
    }
}
