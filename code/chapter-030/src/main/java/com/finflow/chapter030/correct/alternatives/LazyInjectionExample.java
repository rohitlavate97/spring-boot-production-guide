package com.finflow.chapter030.correct.alternatives;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class LazyInjectionExample {
    private final SomeDependency dependency;
    
    // Breaking circular dependency by using @Lazy on the parameter.
    // Spring will inject a proxy here instead of the actual bean, deferring instantiation.
    public LazyInjectionExample(@Lazy SomeDependency dependency) {
        this.dependency = dependency; 
    }
    
    public void executeWork() {
        dependency.doWork(); // Actual bean is resolved on first method invocation
    }
}
