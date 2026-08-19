---
chapter: 60
topic: Spring Boot Auto-Configuration — Conditional Annotations, spring.factories, META-INF, Custom Starters
prerequisite_chapters: [30, 40, 50]
reference_system_node: Payment Service (auto-configuration of DataSource, JPA, Redis, Jackson, custom payment-gateway-starter)
---

# Chapter 060: Spring Boot Auto-Configuration Deep Dive

## 1. Concept

Auto-configuration is the beating heart of Spring Boot. It fundamentally changes how developers interact with the Spring Framework, transitioning the paradigm from "explicitly declare every infrastructure component" to "inherit opinionated defaults that back off when customized."

In the era of traditional Spring, configuring a database meant explicitly wiring a `DataSource`, a `TransactionManager`, an `EntityManagerFactory`, and a dialect. The configuration overhead grew linearly with the application's complexity. 

Spring Boot solves this by introducing auto-configuration: a mechanism that automatically registers infrastructure beans based on classpath presence, property values, and existing bean definitions. 

The progression of Spring configuration illustrates this evolution:
1. **XML Configuration (`applicationContext.xml`)**: Verbose, untyped, refactoring-resistant.
2. **Java Config (`@Configuration`)**: Type-safe, refactoring-friendly, but still requires explicit declaration of all infrastructure.
3. **Auto-Configuration (`@EnableAutoConfiguration`)**: Opinionated, automated, convention-over-configuration.

At its core, `@SpringBootApplication` is a composite annotation comprising:
- `@SpringBootConfiguration` (a specialized `@Configuration`)
- `@ComponentScan` (discovers your business beans like `@Service`, `@Controller`)
- `@EnableAutoConfiguration` (triggers the auto-configuration engine)

> [!IMPORTANT]
> Auto-configuration is fundamentally different from component scanning. Component scanning discovers your *business logic* classes in your defined packages. Auto-configuration discovers *infrastructure* classes (data sources, web servers, message brokers) provided by Spring Boot or third-party starters, typically residing outside your base package.

The overarching contract of auto-configuration is "conditional backing off". Auto-configuration applies opinionated defaults, but the moment you define your own bean of the same type (using `@ConditionalOnMissingBean`), Spring Boot yields control to you.

Historically (Spring Boot 1.x and 2.x), auto-configurations were registered via `META-INF/spring.factories`. In Spring Boot 3.x, this mechanism was entirely replaced by `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` to improve startup performance and avoid loading unnecessary configuration classes prematurely.

## 2. Internal Working

To master auto-configuration, you must understand the boot sequence triggered by `SpringApplication.run()`.

When the application starts, `@EnableAutoConfiguration` imports the `AutoConfigurationImportSelector`. This class is responsible for determining which auto-configuration classes should be evaluated and potentially loaded.

### The Import Phase (Spring Boot 3.x)
1. The `AutoConfigurationImportSelector` searches the classpath for all files named `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
2. It reads these files line-by-line to gather a list of fully qualified class names of auto-configuration classes.
3. It filters this initial list using the `AutoConfigurationMetadata` (if available) and removes any classes explicitly excluded via `spring.autoconfigure.exclude` or `@EnableAutoConfiguration(exclude = ...)`.

### The Condition Evaluation Phase
Once the candidates are selected, Spring doesn't just load them all. It evaluates the `@Conditional` annotations on each class. The results are recorded in the `ConditionEvaluationReport`.

Spring Boot utilizes several core conditions:

*   **`@ConditionalOnClass`**: Evaluates to true only if the specified class is present on the classpath. 
    *   *Internal magic*: Spring uses ASM (a Java bytecode manipulation framework) to read the class metadata of the `@Configuration` class *without actually loading it into the JVM*. If it used standard Java reflection, a `ClassNotFoundException` would be thrown when trying to load an auto-configuration class whose dependencies are missing.
*   **`@ConditionalOnMissingBean` / `@ConditionalOnBean`**: Checks the `BeanFactory` (the bean definition registry) to see if a bean of a specific type or name already exists.
    *   *Internal magic*: Because these conditions depend on the current state of the bean registry, the *order* of evaluation is critical. If `ClassA` checks for the presence of `BeanB`, `BeanB`'s configuration must be evaluated first.
*   **`@ConditionalOnProperty`**: Checks the `Environment` (properties files, environment variables, command-line arguments) to see if a specific property has a specific value or exists at all.

### Ordering Mechanism
Because auto-configurations often depend on one another (e.g., JPA requires a DataSource), ordering is essential. Spring Boot 3.x uses the `@AutoConfiguration` annotation, which natively supports `before`, `after`, and `beforeName`/`afterName` attributes, replacing the older `@AutoConfigureBefore` and `@AutoConfigureAfter` annotations.

```mermaid
graph TD
    A[SpringApplication.run] --> B[@EnableAutoConfiguration]
    B --> C[AutoConfigurationImportSelector]
    C --> D{Read AutoConfiguration.imports}
    D --> E[Filter Exclusions]
    E --> F[Sort via @AutoConfiguration before/after]
    F --> G[Evaluate Conditions using ASM]
    G --> H{Conditions Match?}
    H -- Yes --> I[Register Beans in Context]
    H -- No --> J[Skip Configuration]
    I --> K[Record in ConditionEvaluationReport]
    J --> K
```

## 3. Enterprise Scenario

At FinFlow, the Payment Service is responsible for routing transactions to various external payment providers. As the platform scaled to ~20 pods to handle peak transaction loads, the platform engineering team noticed that multiple microservices (Payment, Order, Billing) were copy-pasting the same complex HTTP client configuration to communicate with our internal Payment Gateway.

To enforce standardization, the team built a custom `payment-gateway-spring-boot-starter`. 

This starter is designed to:
1. Auto-configure a resilient `PaymentGatewayClient`.
2. Wrap the client in Resilience4j circuit breakers and retry logic.
3. Configure a custom Jackson `ObjectMapper` tuned for our internal financial data formats.
4. Back off gracefully if a consuming service needs to define its own highly customized client.

Simultaneously, the Payment Service relies heavily on Spring Boot's built-in auto-configurations: HikariCP for connection pooling, Spring Data JPA for data access, Lettuce for Redis caching, and Actuator for health and metrics. 

Startup time is critical; taking too long to spin up new pods during a traffic spike leads to degraded customer experience. Every auto-configuration condition evaluated adds a microsecond, and broken configurations can cause massive delays or missing infrastructure.

## 4. Incorrect Implementation

The following code illustrates a botched implementation of both the custom starter and the consuming Payment Service. 

### Problem 1: Manual DataSource Override (Payment Service)

The Payment Service developers needed to tweak the database connection pool size, so they explicitly defined a `DataSource` bean, unaware of the blast radius.

```java
package com.finflow.chapter060.incorrect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    // INCORRECT: Manually defining the DataSource completely disables Spring Boot's 
    // DataSourceAutoConfiguration. We lose HikariCP, connection pool metrics, 
    // and Actuator health indicators.
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://db.finflow.internal:5432/payments");
        dataSource.setUsername("payment_user");
        dataSource.setPassword("secure_password");
        return dataSource;
    }
}
```

### Problem 2 & 3: Legacy Registration and Bad Annotations (Custom Starter)

The platform team created the custom starter, but used the legacy Spring Boot 2.x `spring.factories` approach and standard `@Configuration` annotations, leading to strict ordering failures.

**File: `src/main/resources/META-INF/spring.factories` (Legacy)**
```properties
# INCORRECT: This is ignored in Spring Boot 3.x!
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.finflow.chapter060.incorrect.starter.PaymentGatewayAutoConfiguration
```

**File: `PaymentGatewayAutoConfiguration.java`**
```java
package com.finflow.chapter060.incorrect.starter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.web.client.RestTemplate;

// INCORRECT: In Boot 3.x, @AutoConfiguration should be used instead of @Configuration
// for auto-configuration classes to ensure proper ordering and proxying rules.
@Configuration
// INCORRECT: @AutoConfigureAfter only works reliably on classes annotated with @AutoConfiguration.
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnClass(RestTemplate.class)
public class PaymentGatewayAutoConfiguration {

    @Bean
    // INCORRECT: If the consumer defines a PaymentGatewayClient without this exact name, 
    // it might still be registered, causing duplicate beans.
    @ConditionalOnMissingBean(name = "paymentGatewayClient")
    public PaymentGatewayClient paymentGatewayClient() {
        return new PaymentGatewayClient(new RestTemplate(), "http://gateway.finflow.internal");
    }
}
```

```java
package com.finflow.chapter060.incorrect.starter;

public class PaymentGatewayClient {
    private final Object httpClient;
    private final String url;

    public PaymentGatewayClient(Object httpClient, String url) {
        this.httpClient = httpClient;
        this.url = url;
    }
    
    public String executePayment() {
        return "SUCCESS";
    }
}
```

## 5. Production Incident

**Timeline:**
*   **09:00 AM:** The platform team releases `payment-gateway-spring-boot-starter` v2.0, upgrading to Spring Boot 3.3.x.
*   **09:30 AM:** The Payment Service team updates their `build.gradle` and deploys to staging.
*   **09:35 AM:** Staging deployment succeeds, but automated integration tests fail. The `PaymentGatewayClient` bean is missing from the ApplicationContext.
*   **09:50 AM:** The Payment team hotfixes the issue by explicitly adding `@Import(PaymentGatewayAutoConfiguration.class)` to their main application class. This forces the bean to load, bypassing the intended auto-configuration discovery mechanism. Tests pass, and they deploy to production.
*   **11:00 AM:** The Order Service team attempts to upgrade. They have their own specialized `PaymentGatewayClient` defined in their codebase. Because the starter's auto-configuration was not discovered (due to `spring.factories` being ignored), they also use the `@Import` hack. 
*   **11:15 AM:** The Order Service fails to start in production. The `@Import` bypasses the conditional evaluation phase early, and the starter's bean collides with their custom bean, causing a `BeanDefinitionOverrideException`.
*   **02:00 PM (Cascading Failure):** Meanwhile, in the Payment Service, Black Friday traffic spikes. The manual `DriverManagerDataSource` created in Problem 1 creates a new physical connection for *every single database query* instead of pooling them like HikariCP. 
*   **02:15 PM:** The database server exhausts its maximum connection limit. Because `DataSourceAutoConfiguration` was completely disabled, HikariCP was not configured, meaning no connection pool metrics were emitted to Grafana. The monitoring dashboards showed zero active connections, hiding the root cause. The entire FinFlow payment tier goes offline for 3 hours.

## 6. Logs

Running the application with `--debug` reveals the `CONDITIONS EVALUATION REPORT`. 

When the custom starter failed to load, it simply wasn't present in the report at all because `spring.factories` wasn't read:

```text
============================
CONDITIONS EVALUATION REPORT
============================


Positive matches:
-----------------
   ... (other matches) ...

Negative matches:
-----------------
   DataSourceAutoConfiguration:
      Did not match:
         - @ConditionalOnMissingBean (types: javax.sql.DataSource; SearchStrategy: all) found beans of type 'javax.sql.DataSource' dataSource (OnBeanCondition)
```

The Order Service startup failure due to duplicate beans:

```text
2026-11-27 11:15:02.342 ERROR 1 --- [           main] o.s.b.d.LoggingFailureAnalysisReporter   : 

***************************
APPLICATION FAILED TO START
***************************

Description:

The bean 'paymentGatewayClient', defined in class path resource [com/finflow/chapter060/incorrect/starter/PaymentGatewayAutoConfiguration.class], could not be registered. A bean with that name has already been defined in file [D:\Projects\Spring Boot\spring-boot-production-guide\order-service\CustomGatewayConfig.class] and overriding is disabled.

Action:

Consider renaming one of the beans or enabling overriding by setting spring.main.allow-bean-definition-overriding=true
```

## 7. Root Cause Analysis

The incident was a perfect storm of auto-configuration misunderstandings across two teams.

**Root Cause 1: Spring Boot 3.x Migration (The Starter Failure)**
In Spring Boot 2.7, support for `META-INF/spring.factories` was deprecated, and in Spring Boot 3.0, it was completely removed for auto-configuration registration. Because the platform team used the old file format, the `AutoConfigurationImportSelector` completely ignored `PaymentGatewayAutoConfiguration`. It was never evaluated, never loaded, and never registered beans.

**Root Cause 2: Forced Loading defeats Conditions (The Order Service Failure)**
When the Payment team used `@Import(PaymentGatewayAutoConfiguration.class)`, they fundamentally altered the lifecycle. `@Import` processes standard `@Configuration` classes immediately during the standard component scanning phase, *before* the auto-configuration phase. This meant the starter's bean was registered unconditionally, causing a collision when the auto-configuration phase later tried to register the Order Service's custom bean.

**Root Cause 3: Naive Bean Replacement (The Database Outage)**
By defining a raw `DriverManagerDataSource` bean, the Payment team triggered the `@ConditionalOnMissingBean(DataSource.class)` condition inside Spring Boot's internal `DataSourceAutoConfiguration` to evaluate to `false`. 
Auto-configurations are cascades. Because `DataSourceAutoConfiguration` backed off, everything that depended on HikariCP also backed off. No `HikariDataSource` was created, no Hikari metrics were registered with the Micrometer registry, and Actuator health indicators targeting the pool were omitted. The system reverted to creating a new TCP connection for every query, destroying the database under load.

## 8. Debugging Process

When auto-configuration goes wrong, you must inspect the `ConditionEvaluationReport`. 

**Step 1: Enable Debug Logging**
Run the JVM with `--debug` or set `debug=true` in `application.properties`. This forces Spring Boot to print the `CONDITIONS EVALUATION REPORT` to the console upon startup (or failure).

**Step 2: Check for Discovery**
If a custom starter isn't working, do not check the negative matches first. Check if the class is listed in the report *at all*. If it isn't, Spring Boot's `AutoConfigurationImportSelector` didn't find it. 
*Fix: Verify `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` exists and contains the exact fully qualified class name.*

**Step 3: Analyze "Negative matches"**
If the configuration is discovered but beans aren't registering, find the class in the "Negative matches" section. The report will tell you exactly which condition failed. For example, `Did not match: @ConditionalOnMissingBean found beans of type 'javax.sql.DataSource'`.

**Step 4: Analyze Bean Overrides**
If you see `BeanDefinitionOverrideException`, it usually means an auto-configuration class was accidentally component-scanned or explicitly `@Import`ed, causing it to load as a user configuration rather than an auto-configuration, thereby breaking the `@ConditionalOnMissingBean` back-off contract.

**Step 5: Verify Active Context**
Inject the `ApplicationContext` or use the Actuator `/actuator/beans` endpoint to inspect the runtime type of the bean. `applicationContext.getBeansOfType(DataSource.class)` would have immediately revealed that a `DriverManagerDataSource` was active instead of a `HikariDataSource`.

## 9. Correct Implementation

Here is the correct way to implement both the consuming service's database configuration and the custom starter.

### Fixing the Consuming Service (Payment Service)

Instead of replacing the `DataSource`, use `@ConfigurationProperties` to tweak the connection pool, letting Spring Boot's auto-configuration do the heavy lifting of wiring HikariCP.

**File: `application.yml` (Payment Service)**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.finflow.internal:5432/payments
    username: payment_user
    password: secure_password
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 3000
```

### Fixing the Custom Starter

**File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**
```text
com.finflow.chapter060.correct.starter.PaymentGatewayAutoConfiguration
```

**File: `PaymentGatewayProperties.java`**
```java
package com.finflow.chapter060.correct.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

// Best Practice: Externalize configuration to a properties class with validation
@ConfigurationProperties(prefix = "finflow.payment.gateway")
public class PaymentGatewayProperties {

    @NotBlank
    private String url = "http://default-gateway.finflow.internal";
    
    @Min(100)
    private int timeoutMs = 2000;

    // Getters and setters omitted for brevity
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
```

**File: `PaymentGatewayAutoConfiguration.java`**
```java
package com.finflow.chapter060.correct.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

// CORRECT: Use @AutoConfiguration to register as an auto-config class.
@AutoConfiguration
// CORRECT: Enable the properties binding
@EnableConfigurationProperties(PaymentGatewayProperties.class)
// CORRECT: Only run this config if RestTemplate is on the classpath
@ConditionalOnClass(RestTemplate.class)
// CORRECT: Allow users to disable this entire starter via properties
@ConditionalOnProperty(prefix = "finflow.payment.gateway", name = "enabled", matchIfMissing = true)
public class PaymentGatewayAutoConfiguration {

    @Bean
    // CORRECT: Back off if ANY bean of type PaymentGatewayClient exists,
    // not just one with a specific name.
    @ConditionalOnMissingBean(PaymentGatewayClient.class)
    public PaymentGatewayClient paymentGatewayClient(PaymentGatewayProperties properties) {
        RestTemplate restTemplate = new RestTemplate();
        // Configure restTemplate with properties.getTimeoutMs()...
        return new PaymentGatewayClient(restTemplate, properties.getUrl());
    }
}
```

## 10. Performance Comparison

Understanding the performance implications of auto-configuration is crucial for large-scale microservice platforms.

*   **(illustrative) Baseline Spring Boot 3.3 startup (web, data-jpa, auto-config intact):** `4.2s`
    *   Result: HikariCP pool initialized, metrics bound, health endpoints active.
*   **(illustrative) Startup with manual `DriverManagerDataSource` override:** `4.5s`
    *   Result: `DataSourceAutoConfiguration` backs off, but JPA auto-configs struggle and fallback. Observability beans are silently missing. The JVM spends time evaluating conditions that ultimately fail.
*   **(illustrative) Startup with broken `@ConditionalOnBean` ordering:** Adds `200-500ms`.
    *   Result: If an auto-configuration requires a bean that hasn't been evaluated yet, the application context initialization becomes turbulent as Spring attempts to eagerly resolve beans out of order.
*   **(illustrative) Optimized Startup (excluding unused auto-configs via `@SpringBootApplication(exclude=...)`):** `3.9s` (saves `300ms`).
    *   Result: By explicitly telling the `AutoConfigurationImportSelector` to ignore configurations you know you don't need (e.g., `RabbitAutoConfiguration` if you only use Kafka), you save ASM bytecode parsing and evaluation time.

## 11. Best Practices

*   **DO:** Use `@AutoConfiguration` (not `@Configuration`) for auto-configuration classes in Spring Boot 3.x. This ensures they are loaded strictly in the auto-configuration phase and honors ordering attributes.
*   **DO:** Register your auto-configurations strictly in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
*   **DO:** Always use `@ConditionalOnMissingBean` when defining beans in an auto-configuration class. This is the golden rule that allows consuming applications to override your opinionated defaults.
*   **DO:** Use `@ConfigurationProperties` bound to a specific prefix (e.g., `finflow.starter.name`) and use Jakarta Validation (`@Min`, `@NotBlank`) to ensure configuration correctness at startup.
*   **DO:** Exclude auto-configurations you don't need in production using `spring.autoconfigure.exclude` in `application.yml`.
*   **DON'T:** Use `@ConditionalOnBean` in auto-configuration classes unless you are absolutely certain of the ordering. It is highly sensitive to the order in which bean definitions are loaded. Prefer `@ConditionalOnClass` or ensure strict ordering with `@AutoConfiguration(after = ...)`.
*   **DON'T:** Put auto-configuration classes in packages that are covered by `@ComponentScan` in the consuming application. If an auto-configuration class is component-scanned, it is treated as a regular configuration, completely bypassing the conditional evaluation ordering.
*   **DON'T:** Manually define infrastructure beans (like `DataSource`, `EntityManagerFactory`, `Jackson2ObjectMapperBuilder`) unless you intend to completely replace Spring Boot's comprehensive setup. Customize them via properties or `Customizer` interfaces instead.

## 12. Common Mistakes

1. **Boot 3.x Registration Failure:** Upgrading to Spring Boot 3.x but leaving the auto-configuration registration in `spring.factories`. The starter will silently fail to load.
2. **The "All-or-Nothing" Override:** Manually defining a `DataSource` or `ObjectMapper` and unintentionally losing HikariCP metrics, JSON module auto-discovery, and Actuator health indicators.
3. **Condition Ordering Deadlocks:** Using `@ConditionalOnBean(SomeService.class)` when `SomeService` is provided by another auto-configuration that happens to run *after* yours. The condition will evaluate to false incorrectly.
4. **Missing `@EnableConfigurationProperties`:** Defining a `@ConfigurationProperties` class but forgetting to link it to the auto-configuration class, resulting in null configurations.
5. **Untested Conditions:** Deploying custom starters without using `ApplicationContextRunner` to verify that the configurations back off correctly when the consumer overrides them.
6. **Component Scanning the Starter:** Placing the custom starter classes in a package like `com.finflow` when the main application also uses `@SpringBootApplication(scanBasePackages = "com.finflow")`. The starter loads twice, bypassing conditions.

## 13. Interview Questions

*   **Junior:** What is the difference between `@SpringBootApplication` and `@Configuration`? What is the purpose of auto-configuration?
*   **Mid:** Explain how `@ConditionalOnMissingBean` works. If I want to disable Spring Boot's specific auto-configuration completely, how would I do it?
*   **Senior:** Explain the internal mechanics of `AutoConfigurationImportSelector`. How does Spring Boot evaluate `@ConditionalOnClass` without throwing a `ClassNotFoundException` if the class isn't on the classpath? What replaced `spring.factories` in Spring Boot 3.x and why?
*   **Staff:** You are tasked with designing a custom Spring Boot starter for a proprietary internal caching system used by 50 microservices. Outline the Maven module structure. How do you handle configuration properties, conditional logic based on other beans, and backward compatibility?
*   **Principal:** Your platform has 30 microservices sharing 5 custom platform starters. An upgrade to a shared starter causes cascading, intermittent startup failures due to bean definition overrides. Design a comprehensive testing and release strategy for shared starters. How do you utilize `ApplicationContextRunner`, compatibility matrices, and canary deployments to ensure platform stability?

## 14. Hands-on Exercise

**Goal:** Build a robust, testable `payment-gateway-spring-boot-starter`.

1. Create a multi-module Maven/Gradle project: `payment-gateway-starter` (empty, just dependencies) and `payment-gateway-autoconfigure` (the actual code).
2. Create a `PaymentGatewayClient` that takes a timeout and URL.
3. Create `PaymentGatewayProperties` with `@ConfigurationProperties(prefix = "finflow.gateway")` and add Jakarta validation annotations.
4. Create `PaymentGatewayAutoConfiguration` annotated with `@AutoConfiguration`. Provide a `@ConditionalOnMissingBean` for the client.
5. Add an Actuator `HealthIndicator` bean that pings the gateway URL, but only register it if Actuator is on the classpath (`@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")`).
6. Register the class in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
7. **Crucial:** Write a JUnit test using `ApplicationContextRunner` to assert that:
    * The default bean is created.
    * Properties are bound correctly.
    * If a user provides their own `PaymentGatewayClient` bean, the auto-configured one backs off.

## 15. Advanced Challenge

**Dynamic Bean Registration:**

Sometimes, you need to register beans dynamically based on list properties, which standard `@Conditional` annotations cannot handle easily.

Given this property structure:
```yaml
finflow:
  gateways:
    enabled:
      - stripe
      - adyen
```

Build an auto-configuration that uses an `ImportBeanDefinitionRegistrar` or a `BeanDefinitionRegistryPostProcessor` to programmatically register a `StripeGatewayService` and an `AdyenGatewayService` as Spring beans, *only* if they are listed in the `enabled` array. Ensure that if `paypal` is not in the list, no bean is registered for it. Test this programmatically.

## 16. Production Checklist

- [ ] Custom starters use `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (not `spring.factories`).
- [ ] All auto-configuration classes use `@AutoConfiguration` annotation.
- [ ] `@ConditionalOnMissingBean` allows user overrides for all infrastructure beans.
- [ ] `@ConfigurationProperties` classes have Jakarta validation.
- [ ] Auto-config tested with `ApplicationContextRunner` (positive and negative scenarios).
- [ ] Unused auto-configurations excluded via `spring.autoconfigure.exclude`.
- [ ] Startup time profiled with `ApplicationStartup` (Spring Boot 3.x).
- [ ] Starter versioning strategy documented for cross-team consumption.
