# Module 03: Maven, Gradle, Java, and Dependency Problems

## Issue 3.1: Runtime `NoSuchMethodError` & Maven "Nearest-Wins" Diamond Dependency Hell

---

### 1. Scenario

During a production rollout of the **FinFlow Transaction Settlement Pipeline**, a new payment validation filter is deployed that hashes incoming payload streams using Apache Commons Codec:
```java
String hash = DigestUtils.sha256Hex(inputStream);
```

The application compiles cleanly with `mvn clean compile` with zero errors. The unit tests in isolation pass.  
However, when the first live transaction hits the production endpoint, the request crashes with a catastrophic **HTTP 500** and the following fatal runtime error:

```text
java.lang.NoSuchMethodError: 'java.lang.String org.apache.commons.codec.digest.DigestUtils.sha256Hex(java.io.InputStream)'
```

The developer is baffled: *"How can it be `NoSuchMethodError` when the code compiled cleanly in Maven?"*

---

### 2. Symptoms

```text
1. The project compiles successfully without any compile-time errors.
2. At runtime, the first invocation of a library method crashes with:
   java.lang.NoSuchMethodError: 'org.apache.commons.codec.digest.DigestUtils.sha256Hex(Ljava/io/InputStream;)Ljava/lang/String;'
3. Other methods on the same class (such as DigestUtils.sha256Hex(String)) work properly.
4. The issue does not manifest in minimal unit tests that do not pull in the full application dependency tree.
5. Production traffic on the affected endpoint suffers 100% failure rate.
```

---

### 3. Possible Root Causes

1. **Maven "Nearest-Wins" Transitive Dependency Resolution (Most Likely):** A legacy 3rd-party reporting SDK at depth 1 depends on `commons-codec:1.9` (released in 2014, where `sha256Hex(InputStream)` did not exist), while your project transitively expected `commons-codec:1.17.x`. Because the legacy SDK was declared closer to the root POM, Maven picked `1.9` over `1.17.x`!
2. **Runtime vs Compile-Time Classpath Discrepancy:** The compiler used a newer JAR from the compile classpath, but the packaged fat JAR or container runtime loaded an older JAR.
3. **Split Packages / Multiple ClassLoaders:** Multiple JARs on the classpath export the exact same package/class with differing method signatures.
4. **Jakarta EE vs Java EE Namespace Clashes:** A legacy library compiled against `javax.servlet` or `javax.persistence` loaded in Spring Boot 3 (which requires `jakarta.*`).

---

### 4. Architecture Context: Maven Diamond Dependency Conflict

```text
                           FinFlow Core Application (Root POM)
                                     │            │
                    ┌────────────────┘            └───────────────┐
                    │ (Depth 1)                                   │ (Depth 1)
                    ▼                                             ▼
          legacy-reporting-sdk:2.1                       payment-gateway-client:4.0
                    │                                             │
                    │ depends on (Depth 2)                        │ depends on (Depth 2)
                    ▼                                             ▼
           commons-codec:1.9                             commons-codec:1.17.1
       (NO sha256Hex(InputStream))                    (HAS sha256Hex(InputStream))
                    │                                             │
                    └──────────────────────┬──────────────────────┘
                                           │
                        Maven "Nearest-Wins" Rule:
                 Both are at Depth 2 ──► First Declared Wins!
                 If legacy-sdk is declared first ──► 1.9 is chosen!
                                           │
                                           ▼
                                 [NoSuchMethodError at Runtime]
```

---

### 5. How to Reproduce the Issue

#### Step 1: Add Conflicting Dependencies in `pom.xml`
```xml
<dependencies>
    <!-- Legacy dependency declared first pulls in commons-codec:1.9 -->
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
        <version>1.9</version>
    </dependency>
</dependencies>
```

#### Step 2: Invoke Method Introduced in Newer Version
```java
// Method introduced in commons-codec 1.11+
DigestUtils.sha256Hex(inputStream);
```

#### Step 3: Run the Code
Observe that compiling against a modern JDK succeeds if another library had provided it at compile time, but at runtime the JVM throws `NoSuchMethodError`.

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Generate Maven Verbose Dependency Tree
Run `mvn dependency:tree` with `-Dverbose` to see which dependency won the conflict and which versions were omitted:
```bash
mvn dependency:tree -Dverbose -Dincludes=commons-codec:commons-codec
```

**Output:**
```text
[INFO] \- com.finflow:payment-service:jar:1.0.0
[INFO]    +- legacy-reporting-sdk:jar:2.1:compile
[INFO]    |  \- commons-codec:commons-codec:jar:1.9:compile
[INFO]    \- payment-gateway-client:jar:4.0:compile
[INFO]       \- (commons-codec:commons-codec:jar:1.17.1:compile - omitted for conflict with 1.9)
```
**Conclusion:** Maven explicitly omitted `1.17.1` in favor of `1.9` due to declaration order!

#### Method 2: Programmatically Inspect Class Location via `CodeSource`
Use `ClassLoaderDiagnosticService.java`:
```java
ProtectionDomain pd = DigestUtils.class.getProtectionDomain();
URL jarLocation = pd.getCodeSource().getLocation();
System.out.println("Loaded from: " + jarLocation);
```
**Output in broken environment:**
`Loaded from: file:/root/.m2/repository/commons-codec/commons-codec/1.9/commons-codec-1.9.jar`

#### Method 3: Enable JVM Class Loading Telemetry
Run the JVM with `-verbose:class`:
```bash
java -verbose:class -jar app.jar | grep DigestUtils
```
**Output:**
`[0.342s][info][class,load] org.apache.commons.codec.digest.DigestUtils source: jar:file:/app/BOOT-INF/lib/commons-codec-1.9.jar`

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Parse the Fatal Error Message.
        Identify the exact class name and method descriptor:
        Class: org.apache.commons.codec.digest.DigestUtils
        Method: sha256Hex(Ljava/io/InputStream;)Ljava/lang/String;

Step 2: Inspect Which Physical JAR Loaded the Class.
        Call clazz.getProtectionDomain().getCodeSource().getLocation() or use /api/v1/dependencies/inspect-class.

Step 3: Run Maven Dependency Tree.
        Execute: mvn dependency:tree -Dincludes=<groupId>:<artifactId>

Step 4: Check for Omitted Dependencies.
        Search the tree output for "omitted for conflict with...".

Step 5: Enforce Version in <dependencyManagement> or Exclude from Transitive Parent.
```

---

### 8. Technical Root Cause Deep-Dive

#### `ClassNotFoundException` vs `NoClassDefFoundError` vs `NoSuchMethodError`

| Exception / Error | Cause | Lifecycle Phase |
|:---|:---|:---|
| **`ClassNotFoundException`** (Checked Exception) | The `ClassLoader` was explicitly asked to load a class by name (e.g. `Class.forName("com.foo.Bar")`) but the bytecode could not be found anywhere on the classpath. | Runtime dynamic lookup |
| **`NoClassDefFoundError`** (Unchecked `LinkageError`) | The class was present when the calling code was compiled, but at runtime either: (a) the `.class` file is physically missing, or (b) the class's `<clinit>` static initializer threw an exception and the class is marked in an errored state. | Runtime class linkage / initialization |
| **`NoSuchMethodError`** (Unchecked `IncompatibleClassChangeError`) | The class was found and successfully loaded, but the specific method signature (name + parameter types + return type) called by the bytecode does not exist in the loaded version. | Runtime method invocation |
| **`NoSuchFieldError`** (Unchecked `IncompatibleClassChangeError`) | The class was found, but a static or instance field referenced by the calling class has been removed or renamed. | Runtime field access |

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Enforce Version in `<dependencyManagement>` (Best Practice)
Place the required modern library version inside the `<dependencyManagement>` section of your root POM. This forces Maven to use the managed version across all transitive dependencies regardless of tree depth or declaration order:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
            <version>1.17.1</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### ✅ Fix 2: Exclude Transitive Dependency from Outdated Library
```xml
<dependency>
    <groupId>com.legacy.vendor</groupId>
    <artifactId>legacy-reporting-sdk</artifactId>
    <version>2.1</version>
    <exclusions>
        <exclusion>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

#### ✅ Fix 3: Enforce Dependency Convergence via `maven-enforcer-plugin`
Add the Enforcer plugin to your build pipeline to fail the build immediately if any transitive dependency versions diverge:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.5.0</version>
    <executions>
        <execution>
            <id>enforce-dependency-convergence</id>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <dependencyConvergence/>
                    <requireUpperBoundDeps/>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

### 10. Verification

1. **Automated Verification Test:** Run `ChecksumSignatureServiceTest.java` to verify that stream signature hashing executes without `NoSuchMethodError`.
2. **ClassLoader Location Test:** Run `ClassLoaderLocationDiagnosticTest.java` to prove that the runtime loaded `commons-codec-1.17.1.jar`.
3. **CI/CD Pipeline Build:** Execute `mvn clean verify` with the Enforcer plugin active to guarantee zero conflicting transitive versions.

---

### 11. Prevention & Production Readiness

1. **Adopt Spring Boot Dependency Management:** Always inherit versions from `spring-boot-starter-parent` or import `spring-boot-dependencies` BOM.
2. **Ban SNAPSHOT Dependencies in Production:** Enforce strict release versioning.
3. **Automate Dependency Security & Vulnerability Scanning:** Use OWASP Dependency-Check or Snyk in CI/CD.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: How does Maven's "Nearest-Wins" dependency resolution strategy work, and why does it lead to diamond dependency bugs?**
2. **Q: What is the exact difference between `ClassNotFoundException` and `NoClassDefFoundError`?**
3. **Q: Why is `<dependencyManagement>` preferred over individual `<exclusion>` tags in enterprise multi-module projects?**
4. **Q: What is the purpose of the Java 9+ Platform Module System (JPMS) regarding split packages?**
5. **Q: What causes an `UnsupportedClassVersionError` at JVM startup?**

#### Production Incident Questions
1. **Incident:** After adding a new AWS SDK starter, an existing Jackson JSON serialization endpoint begins throwing `NoSuchMethodError: com.fasterxml.jackson.databind.ObjectMapper.coercionConfigDefaults()`. How do you identify the culprit JAR?
2. **Incident:** A Spring Boot 3.3 application fails to start with `ClassNotFoundException: javax.servlet.Filter`. What is the root cause and how do you resolve it?
3. **Incident:** In your CI/CD pipeline, `mvn clean package` fails intermittently on a build agent due to `CorruptArtifactException` in `.m2/repository`. How do you sanitize and prevent local cache corruption?
4. **Incident:** Two third-party libraries include conflicting versions of Netty native epoll libraries (`libnetty_transport_native_epoll_x86_64.so`), causing a fatal JVM core dump on Linux. How do you resolve native link collisions?
5. **Incident:** A developer added `<scope>provided</scope>` to Lombok and Jackson. The code compiles locally but crashes in Docker with `NoClassDefFoundError`. Why?

#### Trick Questions
1. **Trick:** If Artifact A (depth 2) and Artifact B (depth 2) both declare `library-x`, which one wins?
2. **Trick:** If a class fails static initialization (`static { ... throw new RuntimeException(); }`), what exception is thrown on the first attempt to load it vs the second attempt to load it?
3. **Trick:** Does Maven `<dependencyManagement>` add dependencies to your project classpath automatically if they are not declared in `<dependencies>`?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
