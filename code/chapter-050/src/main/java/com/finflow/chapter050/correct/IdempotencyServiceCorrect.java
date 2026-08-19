package com.finflow.chapter050.correct;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyServiceCorrect {

    private final ConcurrentHashMap<String, Boolean> keys = new ConcurrentHashMap<>();
    
    public boolean checkAndStore(String key) {
        // In real life this checks Redis, here we just simulate
        return keys.putIfAbsent(key, true) != null;
    }
}
