package com.finflow.chapter300.domain;

import java.io.Serializable;

public class ContainerRuntimeDiagnostics implements Serializable {

    private int availableProcessors;
    private long maxMemoryMb;
    private long totalMemoryMb;
    private long freeMemoryMb;
    private long usedMemoryMb;
    private String jvmVersion;
    private String osName;
    private long pid;

    public ContainerRuntimeDiagnostics() {}

    public ContainerRuntimeDiagnostics(int availableProcessors, long maxMemoryMb, long totalMemoryMb,
                                       long freeMemoryMb, long usedMemoryMb, String jvmVersion,
                                       String osName, long pid) {
        this.availableProcessors = availableProcessors;
        this.maxMemoryMb = maxMemoryMb;
        this.totalMemoryMb = totalMemoryMb;
        this.freeMemoryMb = freeMemoryMb;
        this.usedMemoryMb = usedMemoryMb;
        this.jvmVersion = jvmVersion;
        this.osName = osName;
        this.pid = pid;
    }

    public int getAvailableProcessors() { return availableProcessors; }
    public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
    public long getMaxMemoryMb() { return maxMemoryMb; }
    public void setMaxMemoryMb(long maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }
    public long getTotalMemoryMb() { return totalMemoryMb; }
    public void setTotalMemoryMb(long totalMemoryMb) { this.totalMemoryMb = totalMemoryMb; }
    public long getFreeMemoryMb() { return freeMemoryMb; }
    public void setFreeMemoryMb(long freeMemoryMb) { this.freeMemoryMb = freeMemoryMb; }
    public long getUsedMemoryMb() { return usedMemoryMb; }
    public void setUsedMemoryMb(long usedMemoryMb) { this.usedMemoryMb = usedMemoryMb; }
    public String getJvmVersion() { return jvmVersion; }
    public void setJvmVersion(String jvmVersion) { this.jvmVersion = jvmVersion; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }
}
