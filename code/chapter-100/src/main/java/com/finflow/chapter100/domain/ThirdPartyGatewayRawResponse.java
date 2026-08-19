package com.finflow.chapter100.domain;

public class ThirdPartyGatewayRawResponse {
    private String rawPan;
    private String authCode;
    private String rawPayload;

    public ThirdPartyGatewayRawResponse() {}

    public ThirdPartyGatewayRawResponse(String rawPan, String authCode, String rawPayload) {
        this.rawPan = rawPan;
        this.authCode = authCode;
        this.rawPayload = rawPayload;
    }

    public String getRawPan() { return rawPan; }
    public void setRawPan(String rawPan) { this.rawPan = rawPan; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
