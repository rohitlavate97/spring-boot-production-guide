package com.finflow.troubleshooting.module14.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLocalContextHolder {

    private static final Logger log = LoggerFactory.getLogger(ThreadLocalContextHolder.class);

    private static final ThreadLocal<String> USER_CONTEXT = new ThreadLocal<>();

    public static void setUser(String username) {
        USER_CONTEXT.set(username);
    }

    public static String getUser() {
        return USER_CONTEXT.get();
    }

    public static void clear() {
        USER_CONTEXT.remove();
    }

    // AutoCloseable scope manager to guarantee ThreadLocal cleanup in try-with-resources
    public static class ContextScope implements AutoCloseable {
        public ContextScope(String username) {
            setUser(username);
        }

        @Override
        public void close() {
            clear();
        }
    }

    public static ContextScope withUser(String username) {
        return new ContextScope(username);
    }
}
