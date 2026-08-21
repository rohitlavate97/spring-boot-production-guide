package com.finflow.chapter360.service;

import com.finflow.chapter360.model.DiscoveredInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service Discovery Registry and Client-Side Load Balancer.
 * Implements Round-Robin instance selection and dynamic registration.
 */
@Service
public class ServiceDiscoveryManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryManager.class);

    // Simulated service registry map: serviceId -> List of Instances
    private final Map<String, List<DiscoveredInstance>> registry = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinIndices = new ConcurrentHashMap<>();

    public ServiceDiscoveryManager() {
        initDefaultTopology();
    }

    private void initDefaultTopology() {
        // Order Service instances (2 pods)
        registerInstance("order-service", new DiscoveredInstance(
                "order-service", "order-svc-pod-1", "order-service-1.finflow.internal", 8082, false, "UP",
                Map.of("zone", "us-east-1a", "version", "2.4.0")
        ));
        registerInstance("order-service", new DiscoveredInstance(
                "order-service", "order-svc-pod-2", "order-service-2.finflow.internal", 8082, false, "UP",
                Map.of("zone", "us-east-1b", "version", "2.4.0")
        ));

        // Ledger Service instances (2 pods)
        registerInstance("ledger-service", new DiscoveredInstance(
                "ledger-service", "ledger-svc-pod-1", "ledger-service-1.finflow.internal", 8083, false, "UP",
                Map.of("zone", "us-east-1a", "version", "1.9.0")
        ));
        registerInstance("ledger-service", new DiscoveredInstance(
                "ledger-service", "ledger-svc-pod-2", "ledger-service-2.finflow.internal", 8083, false, "UP",
                Map.of("zone", "us-east-1b", "version", "1.9.0")
        ));
    }

    public synchronized void registerInstance(String serviceId, DiscoveredInstance instance) {
        registry.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(instance);
        roundRobinIndices.putIfAbsent(serviceId, new AtomicInteger(0));
        log.info("[ServiceRegistry] Registered instance '{}' for service '{}' at {}:{}",
                instance.getInstanceId(), serviceId, instance.getHost(), instance.getPort());
    }

    public List<DiscoveredInstance> getInstances(String serviceId) {
        return registry.getOrDefault(serviceId, Collections.emptyList());
    }

    public Map<String, List<DiscoveredInstance>> getAllServices() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Client-Side Load Balancing: Round-Robin selection over healthy instances.
     */
    public Optional<DiscoveredInstance> chooseInstance(String serviceId) {
        List<DiscoveredInstance> instances = registry.get(serviceId);
        if (instances == null || instances.isEmpty()) {
            log.warn("[LoadBalancer] No registered instances found for service '{}'", serviceId);
            return Optional.empty();
        }

        List<DiscoveredInstance> healthyInstances = instances.stream()
                .filter(i -> "UP".equalsIgnoreCase(i.getStatus()))
                .toList();

        if (healthyInstances.isEmpty()) {
            log.error("[LoadBalancer] All instances of service '{}' are DOWN!", serviceId);
            return Optional.empty();
        }

        AtomicInteger indexCounter = roundRobinIndices.get(serviceId);
        int index = Math.abs(indexCounter.getAndIncrement() % healthyInstances.size());
        DiscoveredInstance selected = healthyInstances.get(index);

        log.debug("[LoadBalancer] Selected instance '{}' ({}:{}) for service '{}'",
                selected.getInstanceId(), selected.getHost(), selected.getPort(), serviceId);

        return Optional.of(selected);
    }

    public synchronized void updateInstanceStatus(String serviceId, String instanceId, String status) {
        List<DiscoveredInstance> instances = registry.get(serviceId);
        if (instances != null) {
            instances.stream()
                    .filter(i -> i.getInstanceId().equalsIgnoreCase(instanceId))
                    .findFirst()
                    .ifPresent(i -> {
                        i.setStatus(status);
                        log.warn("[ServiceRegistry] Instance '{}' status changed to '{}'", instanceId, status);
                    });
        }
    }
}
