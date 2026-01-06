package org.fb.bean.mcpbean;

import java.util.Map;

public class McpInitializeParams {
    private String protocolVersion;
    private Map<String, Object> capabilities;
    private Map<String, Object> clientInfo;

    public static McpInitializeParams of(String protocolVersion, Map<String, Object> capabilities, Map<String, Object> clientInfo) {
        McpInitializeParams params = new McpInitializeParams();
        params.protocolVersion = protocolVersion;
        params.capabilities = capabilities;
        params.clientInfo = clientInfo;
        return params;
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

    public Map<String, Object> getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(Map<String, Object> clientInfo) {
        this.clientInfo = clientInfo;
    }
}
