package com.finflow.troubleshooting.module18.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TimeoutHierarchyCalculator {

    public record TimeoutValidationResult(
            long clientTimeoutMs,
            long gatewayTimeoutMs,
            long springBootAppTimeoutMs,
            long downstreamApiTimeoutMs,
            long nginxKeepaliveTimeoutSec,
            long tomcatKeepaliveTimeoutSec,
            String keepAliveSafetyStatus,
            String timeoutHierarchyStatus,
            List<String> violations,
            List<String> recommendations
    ) {}

    public TimeoutValidationResult validateHierarchy(
            long clientTimeoutMs,
            long gatewayTimeoutMs,
            long springBootAppTimeoutMs,
            long downstreamApiTimeoutMs,
            long nginxKeepaliveTimeoutSec,
            long tomcatKeepaliveTimeoutSec
    ) {
        List<String> violations = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // 1. Keep-Alive Race Condition Check
        String keepAliveStatus;
        if (tomcatKeepaliveTimeoutSec <= nginxKeepaliveTimeoutSec) {
            keepAliveStatus = "CRITICAL_KEEP_ALIVE_RACE_CONDITION";
            violations.add("Tomcat keep-alive timeout (" + tomcatKeepaliveTimeoutSec + "s) is <= Nginx keepalive_timeout ("
                    + nginxKeepaliveTimeoutSec + "s)! Upstream proxy will send requests on closing TCP connections, causing intermittent HTTP 502 Bad Gateway / Connection Reset.");
            recommendations.add("Set server.tomcat.keep-alive-timeout to at least " + (nginxKeepaliveTimeoutSec + 5) + "000ms (Nginx keepalive + 5s).");
        } else {
            keepAliveStatus = "SAFE_KEEP_ALIVE_HIERARCHY";
        }

        // 2. Timeout Hierarchy Check: Client > Gateway > App > Downstream API
        String hierarchyStatus;
        if (gatewayTimeoutMs < downstreamApiTimeoutMs) {
            violations.add("Gateway timeout (" + gatewayTimeoutMs + "ms) is LESS than downstream API timeout ("
                    + downstreamApiTimeoutMs + "ms). Gateway will return 504 Gateway Timeout while backend work continues orphaned!");
        }

        if (clientTimeoutMs > 0 && clientTimeoutMs < gatewayTimeoutMs) {
            violations.add("Client timeout (" + clientTimeoutMs + "ms) is less than Gateway timeout ("
                    + gatewayTimeoutMs + "ms). Client will abort connection before Gateway responds.");
        }

        if (violations.isEmpty()) {
            hierarchyStatus = "VALID_TIMEOUT_HIERARCHY";
        } else {
            hierarchyStatus = "INVALID_TIMEOUT_HIERARCHY";
        }

        recommendations.add("Enforce: Downstream Client Read Timeout (" + downstreamApiTimeoutMs + "ms) < Gateway Timeout ("
                + gatewayTimeoutMs + "ms) < Ingress Timeout.");
        recommendations.add("Enable proxy_next_upstream for idempotent HTTP methods (GET, HEAD, PUT, DELETE) on error/timeout.");

        return new TimeoutValidationResult(
                clientTimeoutMs,
                gatewayTimeoutMs,
                springBootAppTimeoutMs,
                downstreamApiTimeoutMs,
                nginxKeepaliveTimeoutSec,
                tomcatKeepaliveTimeoutSec,
                keepAliveStatus,
                hierarchyStatus,
                violations,
                recommendations
        );
    }
}
