package com.finflow.troubleshooting.module14.controller;

import com.finflow.troubleshooting.module14.cache.BoundedCacheService;
import com.finflow.troubleshooting.module14.context.ThreadLocalContextHolder;
import com.finflow.troubleshooting.module14.service.MemoryDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryDiagnosticsController {

    private final BoundedCacheService cacheService;
    private final MemoryDiagnosticsService memoryDiagnosticsService;

    public MemoryDiagnosticsController(BoundedCacheService cacheService,
                                       MemoryDiagnosticsService memoryDiagnosticsService) {
        this.cacheService = cacheService;
        this.memoryDiagnosticsService = memoryDiagnosticsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMemoryStats() {
        return ResponseEntity.ok(memoryDiagnosticsService.getMemoryStatistics());
    }

    @PostMapping("/cache/put")
    public ResponseEntity<Map<String, Object>> putCache(@RequestParam String key, @RequestParam String value) {
        cacheService.putBounded(key, value);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "currentSize", cacheService.getBoundedSize(),
                "maxCapacity", cacheService.getMaxCacheEntries()
        ));
    }

    @GetMapping("/cache/get")
    public ResponseEntity<Map<String, Object>> getCache(@RequestParam String key) {
        String val = cacheService.getBounded(key);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "value", val != null ? val : "NOT_FOUND"
        ));
    }

    @PostMapping("/leak")
    public ResponseEntity<Map<String, Object>> injectLeak(@RequestParam String key) {
        cacheService.leakMemory(key);
        return ResponseEntity.ok(Map.of(
                "status", "LEAKED_1MB",
                "unboundedCacheSize", cacheService.getUnboundedCacheSize()
        ));
    }

    @GetMapping("/context/user")
    public ResponseEntity<Map<String, Object>> getContextUser() {
        String user = ThreadLocalContextHolder.getUser();
        return ResponseEntity.ok(Map.of(
                "currentUser", user != null ? user : "ANONYMOUS"
        ));
    }
}
