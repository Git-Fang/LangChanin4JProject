package org.fb.bean.mcpbean;

import lombok.Data;

@Data
public class ToolDefinition {
    private String name;
    private String description;
    private Object inputSchema;
    private Object outputSchema;

    public static ToolDefinition of(String name, String desc, Object input, Object output) {
        ToolDefinition td = new ToolDefinition();
        td.name = name;
        td.description = desc;
        td.inputSchema = input;
        td.outputSchema = output;
        return td;
    }
}