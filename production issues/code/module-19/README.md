# Module 19: Redis Caching: Stampede, Avalanche & Invalidation

## Overview
This module explores enterprise Redis caching architectures, deep-diving into the three classic distributed cache failure modes: **Cache Stampede (Thundering Herd)**, **Cache Avalanche (Synchronized Expiration)**, and **Cache Penetration (Non-Existent Keys)**, along with dual-write consistency patterns and serialization safety.

## Key Scenarios Covered
1. **The Cache Stampede (Thundering Herd):**
   - Why hot-key expiration causes thousands of concurrent requests to overwhelm the database simultaneously.
   - Mitigating stampedes using Distributed Mutex Locks (`SETNX` with retry backoff) and Probabilistic Early Expiration (XFetch algorithm).
2. **The Cache Avalanche (Synchronized Expiration):**
   - Preventing catastrophic database spikes when bulk-warmed keys expire at the exact same second by applying randomized TTL jitter (`TTL = BaseTTL + RandomJitter`).
3. **The Cache Penetration & Null Sentinel Protection:**
   - Protecting against DoS attacks on non-existent IDs by caching sentinel `NULL_VALUE` objects with short TTLs.
4. **Dual-Write Consistency & Invalidation:**
   - Preventing stale data race conditions using Delayed Double Deletion (`Update DB -> Del Cache -> Sleep 500ms -> Del Cache`).
5. **Production Redis Serialization:**
   - Replacing bulky, vulnerable Java native serialization (`JdkSerializationRedisSerializer`) with Jackson JSON (`GenericJackson2JsonRedisSerializer`).

## Project Structure
- `src/main/java/.../service/`:
  - `CacheStampedeGuardService.java` (Implements mutex-guarded cache loading, jittered TTLs, and null value caching).
  - `SimulatedDatabaseService.java` (Database simulator tracking query load).
- `src/main/java/.../config/`:
  - `RedisResilienceConfig.java` (Custom `RedisTemplate` with Jackson JSON serializers).
- `src/main/java/.../controller/`:
  - `RedisDiagnosticsController.java` (REST endpoints for cache inspection, FX rates, accounts, and stampede simulation).
- `src/test/java/.../`:
  - `CacheStampedeGuardTest.java`
  - `CacheJitterCalculatorTest.java`
  - `CachePenetrationGuardTest.java`
  - `RedisDiagnosticsControllerTest.java`
  - `Module19IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 19 Documentation](../../docs/module-19-redis-caching-stampede-avalanche-invalidation.md).
