package com.finflow.troubleshooting.module18.controller;

import com.finflow.troubleshooting.module18.service.TimeoutHierarchyCalculator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class GatewayProxyDiagnosticsController {

    private final TimeoutHierarchyCalculator timeoutCalculator;

    @Value("${server.tomcat.keep-alive-timeout:70000}")
    private long tomcatKeepaliveTimeoutMs;

    @Value("${gateway.proxy.nginx-keepalive-timeout-sec:65}")
    private long nginxKeepaliveTimeoutSec;

    @Value("${gateway.proxy.gateway-read-timeout-ms:10000}")
    private long gatewayReadTimeoutMs;

    @Value("${gateway.proxy.client-read-timeout-ms:8000}")
    private long downstreamApiTimeoutMs;

    public GatewayProxyDiagnosticsController(TimeoutHierarchyCalculator timeoutCalculator) {
        this.timeoutCalculator = timeoutCalculator;
    }

    @GetMapping("/gateway/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("tomcatKeepAliveTimeoutSec", tomcatKeepaliveTimeoutMs / 1000);
        diag.put("nginxKeepAliveTimeoutSec", nginxKeepaliveTimeoutSec);
        diag.put("gatewayReadTimeoutMs", gatewayReadTimeoutMs);
        diag.put("downstreamClientReadTimeoutMs", downstreamApiTimeoutMs);
        diag.put("keepAliveSafety", (tomcatKeepaliveTimeoutMs / 1000) > nginxKeepaliveTimeoutSec ? "SAFE" : "RISKY");
        return ResponseEntity.ok(diag);
    }

    @GetMapping("/gateway/validate-timeouts")
    public ResponseEntity<TimeoutHierarchyCalculator.TimeoutValidationResult> validateTimeouts(
            @RequestParam(defaultValue = "15000") long clientTimeoutMs,
            @RequestParam(defaultValue = "10000") long gatewayTimeoutMs,
            @RequestParam(defaultValue = "9000") long springBootAppTimeoutMs,
            @RequestParam(defaultValue = "8000") long downstreamApiTimeoutMs,
            @RequestParam(defaultValue = "65") long nginxKeepaliveTimeoutSec,
            @RequestParam(defaultValue = "70") long tomcatKeepaliveTimeoutSec
    ) {
        var result = timeoutCalculator.validateHierarchy(
                clientTimeoutMs,
                gatewayTimeoutMs,
                springBootAppTimeoutMs,
                downstreamApiTimeoutMs,
                nginxKeepaliveTimeoutSec,
                tomcatKeepaliveTimeoutSec
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payments/authorize")
    public ResponseEntity<Map<String, Object>> authorizePayment(
            @RequestParam(defaultValue = "50") long delayMs,
            @RequestParam(defaultValue = "100.00") double amount
    ) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        return ResponseEntity.ok(Map.of(
                "status", "AUTHORIZED",
                "authCode", "AUTH-" + UUID.randomUUID().toString().substring(0, 8),
                "amount", amount,
                "processingTimeMs", delayMs
        ));
    }

    @GetMapping("/statements/export")
    public ResponseEntity<Map<String, Object>> exportStatement(@RequestParam(defaultValue = "32") int sizeKb) {
        List<Map<String, Object>> items = new ArrayList<>();
        int count = Math.max(1, sizeKb * 5); // Approximate rows for target size
        for (int i = 0; i < count; i++) {
            items.add(Map.of(
                    "transactionId", "TXN-" + (100000 + i),
                    "timestamp", "2026-08-22T14:30:00Z",
                    "description", "FinFlow Corporate Wire Clearing Settlement Batch Record Index " + i,
                    "amount", 1250.75 + i,
                    "status", "SETTLED"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "accountNumber", "ACC-99887766",
                "totalRecords", count,
                "requestedSizeKb", sizeKb,
                "records", items
        ));
    }

    @GetMapping("/headers/echo")
    public ResponseEntity<Map<String, Object>> echoHeaders(HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scheme", request.getScheme());
        details.put("serverName", request.getServerName());
        details.put("serverPort", request.getServerPort());
        details.put("remoteAddr", request.getRemoteAddr());
        details.put("isSecure", request.isSecure());

        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        details.put("headers", headers);

        return ResponseEntity.ok(details);
    }
}
