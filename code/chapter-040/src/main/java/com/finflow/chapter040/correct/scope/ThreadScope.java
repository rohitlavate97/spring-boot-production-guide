package com.finflow.chapter040.correct.scope;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ThreadScope implements Scope {

    private static final ThreadLocal<Map<String, Object>> threadScope = 
            ThreadLocal.withInitial(HashMap::new);
            
    private static final ThreadLocal<Map<String, Runnable>> destructionCallbacks = 
            ThreadLocal.withInitial(HashMap::new);

    @NonNull
    @Override
    public Object get(@NonNull String name, @NonNull ObjectFactory<?> objectFactory) {
        Map<String, Object> scopeMap = threadScope.get();
        return scopeMap.computeIfAbsent(name, k -> objectFactory.getObject());
    }

    @Override
    public Object remove(@NonNull String name) {
        Map<String, Object> scopeMap = threadScope.get();
        destructionCallbacks.get().remove(name);
        return scopeMap.remove(name);
    }

    @Override
    public void registerDestructionCallback(@NonNull String name, @NonNull Runnable callback) {
        destructionCallbacks.get().put(name, callback);
    }

    @Override
    public Object resolveContextualObject(@NonNull String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread().getName();
    }
    
    /**
     * MUST be called when returning thread to the pool to prevent leaks.
     */
    public static void cleanup() {
        for (Runnable callback : destructionCallbacks.get().values()) {
            try {
                callback.run();
            } catch (Exception e) {
                // Log and continue cleanup
            }
        }
        destructionCallbacks.remove();
        threadScope.remove();
    }
}
