package com.finflow.troubleshooting.module14.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BoundedCacheService {

    private static final Logger log = LoggerFactory.getLogger(BoundedCacheService.class);

    private static final int MAX_CACHE_ENTRIES = 5;

    // ❌ ANTI-PATTERN: Unbounded static cache causes OutOfMemoryError under high load
    private final Map<String, byte[]> unboundedMemoryLeakCache = new ConcurrentHashMap<>();

    // ✅ BEST PRACTICE: Bounded LRU cache with automatic eviction
    private final Map<String, String> boundedLruCache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    public void putBounded(String key, String value) {
        boundedLruCache.put(key, value);
    }

    public String getBounded(String key) {
        return boundedLruCache.get(key);
    }

    public int getBoundedSize() {
        return boundedLruCache.size();
    }

    public int getMaxCacheEntries() {
        return MAX_CACHE_ENTRIES;
    }

    // Leaks 1MB byte buffer per invocation
    public void leakMemory(String key) {
        unboundedMemoryLeakCache.put(key, new byte[1024 * 1024]);
        log.warn("[LEAK] Injected 1MB into unbounded cache. Total items: {}", unboundedMemoryLeakCache.size());
    }

    public int getUnboundedCacheSize() {
        return unboundedMemoryLeakCache.size();
    }

    public void clearUnboundedCache() {
        unboundedMemoryLeakCache.clear();
    }
}
