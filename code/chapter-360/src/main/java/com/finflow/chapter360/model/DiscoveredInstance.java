package com.finflow.chapter360.model;

import java.util.Map;

public class DiscoveredInstance {

    private String serviceId;
    private String instanceId;
    private String host;
    private int port;
    private boolean secure;
    private String status;
    private Map<String, String> metadata;

    public DiscoveredInstance() {
    }

    public DiscoveredInstance(String serviceId, String instanceId, String host, int port,
                              boolean secure, String status, Map<String, String> metadata) {
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.status = status;
        this.metadata = metadata;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
