# Module 02: Configuration and Environment Problems

## Issue 2.1: Production Configuration Drift & The 17-Level Property Precedence Trap

---

### 1. Scenario

During a major payment gateway migration, the **FinFlow Core Payment Service** is configured via `application-prod.yml` to route transactions to the new v2 endpoint `https://api-prod.finflow.com/v1` with a strict client timeout of `2500ms`.

However, in production, payments are failing with `SocketTimeoutException` after exactly `5000ms`, and telemetry reveals the microservice is unexpectedly attempting to connect to `https://api-dev.finflow.com/v1`! 

The developer insists: *"I checked `application-prod.yml` in the Git repository and it is 100% correct!"*

---

### 2. Symptoms

```text
1. Outbound HTTP requests to the payment gateway time out at 5000ms instead of the configured 2500ms.
2. Transactions fail with:
   "java.net.SocketTimeoutException: Connect timed out to https://api-dev.finflow.com/v1"
3. The active Spring profile is verified to be 'prod' (-Dspring.profiles.active=prod).
4. Code and YAML in Git show:
   finflow.core.gateway-url: "https://api-prod.finflow.com/v1"
   finflow.core.timeout-ms: 2500
5. Production telemetry indicates requests are hitting the DEV endpoint.
```

---

### 3. Possible Root Causes

1. **OS / Container Environment Variable Shadowing (Most Likely):** An outdated environment variable in the Kubernetes Deployment spec (e.g. `FINFLOW_CORE_GATEWAY_URL`) shadows `application-prod.yml` because OS environment variables have higher precedence (Level 10) than packaged profile-specific YAML files (Level 14).
2. **Command-Line Argument Override:** A Helm chart or Docker entrypoint passes `--finflow.core.timeout-ms=5000` (Level 4 precedence).
3. **External Config Directory Drift:** A legacy `/config/application.yml` file mounted from a persistent volume overrides packaged classpath configurations.
4. **Relaxed Binding Ambiguity:** Mixing snake_case, kebab-case, and dot-notation causes unexpected property bindings.
5. **Spring Cloud Config Server Caching:** Pods refreshed without re-reading updated configuration files.

---

### 4. Architecture Context: Spring Boot's 17-Level Property Precedence Hierarchy

When Spring Boot builds the `Environment`, it evaluates property sources in strict descending order of precedence (highest to lowest):

```text
 1. Devtools global settings (~/.spring-boot-devtools.properties)
 2. @TestPropertySource annotations on test classes
 3. @SpringBootTest#properties annotation attribute
 4. Command-line arguments (--server.port=9090)                   ◄── HIGH
 5. SPRING_APPLICATION_JSON (inline JSON embedded in env/CLI)
 6. ServletConfig init parameters
 7. ServletContext init parameters
 8. JNDI attributes (java:comp/env)
 9. Java System Properties (System.getProperties(), -Dflags)    ◄── HIGH
10. OS Environment Variables (export FINFLOW_CORE_TIMEOUT_MS)   ◄── CRITICAL OVERRIDE
11. RandomValuePropertySource (random.*)
12. Profile-specific config outside JAR (config/application-{profile}.yml)
13. Profile-specific config inside JAR (classpath:application-{profile}.yml) ◄── COMMITTED IN GIT
14. Application config outside JAR (config/application.yml)
15. Application config inside JAR (classpath:application.yml)   ◄── LOW
16. @PropertySource annotations on @Configuration classes
17. Default properties (SpringApplication.setDefaultProperties)
```

> **Key Rule:** An OS Environment Variable (Level 10) will **always** override a classpath `application-prod.yml` (Level 13)!

---

### 5. How to Reproduce the Issue

#### Step 1: Define `@ConfigurationProperties` with Jakarta Validation
```java
@ConfigurationProperties(prefix = "finflow.core")
@Validated
public class FinFlowCoreProperties {
    @NotBlank
    private String gatewayUrl;

    @Min(100)
    @Max(30000)
    private int timeoutMs;
    
    // Getters and Setters
}
```

#### Step 2: Set Committed `application-prod.yml`
```yaml
finflow:
  core:
    gateway-url: "https://api-prod.finflow.com/v1"
    timeout-ms: 2500
```

#### Step 3: Simulate Legacy Environment Variable in Deployment
```bash
# In Kubernetes Pod or Local Shell:
export FINFLOW_CORE_GATEWAY_URL="https://api-dev.finflow.com/v1"
export FINFLOW_CORE_TIMEOUT_MS="5000"
```

#### Step 4: Run Application
Execute the application with `--spring.profiles.active=prod`. Observe that the environment variables silently take precedence over `application-prod.yml`.

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Query Spring Boot Actuator `/actuator/env`
Send an authenticated request to the Actuator environment endpoint:
```bash
curl -s http://localhost:8082/actuator/env/finflow.core.gateway-url | jq .
```

**Output:**
```json
{
  "property": {
    "source": "systemEnvironment",
    "value": "https://api-dev.finflow.com/v1"
  },
  "propertySources": [
    {
      "name": "systemEnvironment",
      "property": {
        "value": "https://api-dev.finflow.com/v1",
        "origin": "SystemEnvironmentPropertySourceOrigin"
      }
    },
    {
      "name": "Config resource 'class path resource [application-prod.yml]' via location 'optional:classpath:/'",
      "property": {
        "value": "https://api-prod.finflow.com/v1",
        "origin": "URL [jar:file:/app/app.jar!/BOOT-INF/classes!/application-prod.yml]:3:18"
      }
    }
  ]
}
```
**Conclusion:** The Actuator output clearly reveals that `systemEnvironment` is the winning source and shadows `application-prod.yml`!

#### Method 2: Programmatic Property Lineage Inspector
Using `EnvironmentInspectorService.java` from the reproduction lab:
```bash
curl -s "http://localhost:8082/api/v1/config/inspect?key=finflow.core.gateway-url" | jq .
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check Current Runtime Value.
        Invoke /actuator/env/<property.name> or log properties at startup.

Step 2: Inspect the Winning PropertySource.
        Identify if the winning source is systemEnvironment, commandLineArgs, or applicationConfig.

Step 3: Audit Kubernetes Deployment Manifests & ConfigMaps.
        Check if env or envFrom in the pod spec injects stale environment variables.

Step 4: Verify Relaxed Binding Equivalents.
        Check all canonical variants:
        - finflow.core.gateway-url
        - finflow.core.gatewayUrl
        - FINFLOW_CORE_GATEWAY_URL
        - FINFLOW_CORE_GATEWAYURL

Step 5: Verify JSR-380 Validation Failures.
        Ensure invalid values fail fast at startup instead of producing silent runtime bugs.
```

---

### 8. Technical Root Cause Deep-Dive

#### Relaxed Binding 2.0 Engine Mechanics

Spring Boot uses `ConfigurationPropertyName` and `Binder` to resolve properties across diverse naming conventions:

| Source Format | Example | Relaxed Binding Match |
|:---|:---|:---|
| **YAML / Properties** | `finflow.core.timeout-ms` | Canonical kebab-case |
| **System Properties** | `-Dfinflow.core.timeoutMs=5000` | CamelCase or dot-notation |
| **Environment Variable** | `FINFLOW_CORE_TIMEOUT_MS=5000` | Uppercase with underscores |

Under the hood:
1. Underscores (`_`) in environment variables are converted to dots (`.`) or hyphens (`-`).
2. Double underscores (`__`) are converted to dots (`.`) for keys containing hyphens.
3. If both `FINFLOW_CORE_TIMEOUT_MS` and `finflow.core.timeout-ms` exist in different property sources, the source with higher precedence on the `MutablePropertySources` list wins unconditionally.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Clean Up Deployment Manifests & Helm Values
Remove deprecated environment variables from Kubernetes Deployment specs:
```yaml
# kubernetes/deployment.yaml
spec:
  containers:
    - name: payment-service
      env:
        # REMOVE stale overrides:
        # - name: FINFLOW_CORE_GATEWAY_URL
        #   value: "https://api-dev.finflow.com/v1"
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
```

#### ✅ Fix 2: Strict Fail-Fast Validation on `@ConfigurationProperties`
```java
@ConfigurationProperties(prefix = "finflow.core")
@Validated
public class FinFlowCoreProperties {

    @NotBlank(message = "Gateway URL must be explicitly configured")
    @org.hibernate.validator.constraints.URL(message = "Gateway URL must be a valid HTTPS URL")
    private String gatewayUrl;

    @Min(value = 100, message = "Timeout cannot be less than 100ms")
    @Max(value = 10000, message = "Timeout cannot exceed 10s in production")
    private int timeoutMs;
}
```

#### ✅ Fix 3: Kubernetes ConfigMap Rollout Trigger via SHA-256 Checksum
When using ConfigMaps mounted as volumes, pod specifications should include an annotation hash to force rolling restarts upon configuration changes:
```yaml
spec:
  template:
    metadata:
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
```

---

### 10. Verification

1. **Automated Validation Test:** Run `ConfigurationPropertiesValidationTest.java` to verify that out-of-bounds timeout values throw `BindValidationException` on startup.
2. **Relaxed Binding Test:** Run `RelaxedBindingTest.java` to assert that snake_case and environment variable formats bind accurately to the target Java bean.
3. **Actuator Endpoint Audit:** Verify that `/actuator/env` shows the expected configuration origin.

---

### 11. Prevention & Production Readiness

1. **Never Hardcode Secrets in YAML:** Use Kubernetes Secrets or HashiCorp Vault.
2. **Never Use Unvalidated `@Value` Annotations:** Replace loose `@Value("${finflow.timeout}")` with strongly-typed `@ConfigurationProperties` + `@Validated`.
3. **Sanitize Sensitive Properties in Actuator:**
   ```yaml
   management:
     endpoint:
       env:
         show-values: when_authorized
         roles: ADMIN
   ```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the exact precedence order between an OS environment variable, a Java system property (`-D`), and `application-prod.yml`?**
2. **Q: How does Spring Boot's Relaxed Binding 2.0 convert `FINFLOW_PAYMENT_GATEWAY_URL` to Java fields?**
3. **Q: What is the difference between `@Value` and `@ConfigurationProperties` regarding validation, relaxed binding, and SpEL?**
4. **Q: How does Spring Boot handle list and map binding from environment variables?**
5. **Q: Why does modifying a mounted Kubernetes ConfigMap file sometimes fail to update Spring Boot application beans without a pod restart?**

#### Production Incident Questions
1. **Incident:** A developer added `-Dspring.profiles.active=prod` to the JVM arguments, but the application loaded properties from `application-dev.yml`. How could this happen?
2. **Incident:** A secret was updated in AWS Secrets Manager, but the Spring Boot pods continued using the expired credentials until manually restarted. How do you implement automated hot-reloading?
3. **Incident:** In production, `/actuator/env` exposes sensitive database passwords in plaintext. How do you mask sensitive keys while allowing engineers to troubleshoot non-sensitive properties?
4. **Incident:** A YAML configuration contains `port: 080`. Spring Boot fails to start or binds to an unexpected port. Why?
5. **Incident:** A Kubernetes ConfigMap mounted using `subPath` does not receive configuration updates when the ConfigMap is modified in the cluster. Why?

#### Trick Questions
1. **Trick:** If a property is defined in both `application.properties` and `application.yml` in the same directory, which one takes precedence?
2. **Trick:** Does `@Validated` on a `@ConfigurationProperties` class validate nested object properties automatically without `@Valid` on the nested field?
3. **Trick:** If you define `server.port=8080` in `application.yml` and pass `--server.port=9090` as a program argument, can an environment variable `SERVER_PORT=7070` override the command-line argument?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
