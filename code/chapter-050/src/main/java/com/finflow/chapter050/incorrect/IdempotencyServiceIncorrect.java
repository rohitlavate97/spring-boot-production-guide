package com.finflow.chapter050.incorrect;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyServiceIncorrect {

    private ConcurrentHashMap<String, Boolean> keys = new ConcurrentHashMap<>();
    
    // INCORRECT: Setter injection for a required dependency allows state mutation after construction
    private String redisPrefix;

    public void setRedisPrefix(String redisPrefix) {
        this.redisPrefix = redisPrefix;
    }

    public boolean checkAndStore(String key) {
        // In real life this checks Redis, here we just simulate
        return keys.putIfAbsent(key, true) != null;
    }
}
