package org.fb.bean.mcpbean;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Object inputSchema) {
        this.inputSchema = inputSchema;
    }

    public Object getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Object outputSchema) {
        this.outputSchema = outputSchema;
    }
}