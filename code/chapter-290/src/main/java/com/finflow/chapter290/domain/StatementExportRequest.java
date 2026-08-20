package com.finflow.chapter290.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class StatementExportRequest implements Serializable {

    private String requestId;
    private String merchantId;
    private int month;
    private int year;
    private String format; // PDF, CSV, FATAL_ERROR
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private String generatedFileUrl;
    private Instant submittedAt;
    private Instant completedAt;

    public StatementExportRequest() {}

    public StatementExportRequest(String requestId, String merchantId, int month, int year,
                                  String format, String status, String generatedFileUrl,
                                  Instant submittedAt, Instant completedAt) {
        this.requestId = requestId;
        this.merchantId = merchantId;
        this.month = month;
        this.year = year;
        this.format = format;
        this.status = status;
        this.generatedFileUrl = generatedFileUrl;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGeneratedFileUrl() { return generatedFileUrl; }
    public void setGeneratedFileUrl(String generatedFileUrl) { this.generatedFileUrl = generatedFileUrl; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatementExportRequest that)) return false;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "StatementExportRequest{" +
                "requestId='" + requestId + '\'' +
                ", merchantId='" + merchantId + '\'' +
                ", format='" + format + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
