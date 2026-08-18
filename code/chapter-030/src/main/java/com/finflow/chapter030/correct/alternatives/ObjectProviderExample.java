package com.finflow.chapter030.correct.alternatives;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ObjectProviderExample {
    private final ObjectProvider<SomeDependency> dependencyProvider;
    
    // Breaking circular dependency using ObjectProvider.
    // The dependency is not resolved until getObject() is called.
    public ObjectProviderExample(ObjectProvider<SomeDependency> dependencyProvider) {
        this.dependencyProvider = dependencyProvider;
    }
    
    public void executeWork() {
        SomeDependency dep = dependencyProvider.getObject(); // Resolved here
        dep.doWork();
    }
}
