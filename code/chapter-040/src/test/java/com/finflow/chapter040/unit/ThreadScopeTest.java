package com.finflow.chapter040.unit;

import com.finflow.chapter040.correct.scope.ThreadScope;
import com.finflow.chapter040.correct.scope.ThreadScopeRegistrar;
import com.finflow.chapter040.correct.scope.ThreadScoped;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ThreadScopeTest {

    static class SimpleThreadScopedBean {
        private final UUID id = UUID.randomUUID();
        
        public UUID getId() {
            return id;
        }
    }

    @Configuration
    static class Config {
        @Bean
        public static ThreadScopeRegistrar threadScopeRegistrar() {
            return new ThreadScopeRegistrar();
        }

        @Bean
        @ThreadScoped
        public SimpleThreadScopedBean threadScopedBean() {
            return new SimpleThreadScopedBean();
        }
    }

    @Test
    void threadScopeGivesDifferentInstancesPerThread() throws InterruptedException {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);

        // Bean retrieved on main thread
        SimpleThreadScopedBean beanMain = context.getBean(SimpleThreadScopedBean.class);
        SimpleThreadScopedBean beanMain2 = context.getBean(SimpleThreadScopedBean.class);

        // Same thread gets same instance identity via getId()
        assertEquals(beanMain.getId(), beanMain2.getId());

        // Thread 2 gets different instance
        AtomicReference<UUID> thread2BeanId = new AtomicReference<>();
        Thread t2 = new Thread(() -> {
            SimpleThreadScopedBean bean = context.getBean(SimpleThreadScopedBean.class);
            thread2BeanId.set(bean.getId());
            ThreadScope.cleanup(); // Clean up at end of task
        });

        t2.start();
        t2.join();

        assertNotEquals(beanMain.getId(), thread2BeanId.get(), "Different threads should get different bean instances");

        context.close();
    }
}
