package org.fb.bean.mcpbean;

import java.util.Map;

public class McpInitializeResult {
    private String protocolVersion;
    private Map<String, Object> capabilities;
    private Map<String, Object> serverInfo;

    public static McpInitializeResult of(String protocolVersion, Map<String, Object> capabilities, Map<String, Object> serverInfo) {
        McpInitializeResult result = new McpInitializeResult();
        result.protocolVersion = protocolVersion;
        result.capabilities = capabilities;
        result.serverInfo = serverInfo;
        return result;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public Map<String, Object> getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(Map<String, Object> serverInfo) {
        this.serverInfo = serverInfo;
    }
}
