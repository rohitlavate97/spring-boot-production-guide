package com.finflow.chapter300.domain;

import java.io.Serializable;

public class MemoryLimitCalculation implements Serializable {

    private long containerLimitMb;
    private double maxRamPercentage;
    private long calculatedHeapLimitMb;
    private long offHeapBufferMb;
    private String recommendation;

    public MemoryLimitCalculation() {}

    public MemoryLimitCalculation(long containerLimitMb, double maxRamPercentage,
                                  long calculatedHeapLimitMb, long offHeapBufferMb,
                                  String recommendation) {
        this.containerLimitMb = containerLimitMb;
        this.maxRamPercentage = maxRamPercentage;
        this.calculatedHeapLimitMb = calculatedHeapLimitMb;
        this.offHeapBufferMb = offHeapBufferMb;
        this.recommendation = recommendation;
    }

    public long getContainerLimitMb() { return containerLimitMb; }
    public void setContainerLimitMb(long containerLimitMb) { this.containerLimitMb = containerLimitMb; }
    public double getMaxRamPercentage() { return maxRamPercentage; }
    public void setMaxRamPercentage(double maxRamPercentage) { this.maxRamPercentage = maxRamPercentage; }
    public long getCalculatedHeapLimitMb() { return calculatedHeapLimitMb; }
    public void setCalculatedHeapLimitMb(long calculatedHeapLimitMb) { this.calculatedHeapLimitMb = calculatedHeapLimitMb; }
    public long getOffHeapBufferMb() { return offHeapBufferMb; }
    public void setOffHeapBufferMb(long offHeapBufferMb) { this.offHeapBufferMb = offHeapBufferMb; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}
