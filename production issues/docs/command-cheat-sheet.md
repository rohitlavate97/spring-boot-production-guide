# Master Diagnostic Command Cheat Sheet

## Essential Production Troubleshooting Commands for Java, Spring Boot, Linux, PostgreSQL, Kafka, Redis & Kubernetes

---

### 1. JVM Performance & Diagnostics

```bash
# 1. List running Java processes with JVM arguments
jcmd -l

# 2. Capture a live JVM Thread Dump
jcmd <PID> Thread.print > /tmp/thread_dump.tdump
# Or via jstack:
jstack -l <PID> > /tmp/jstack.tdump

# 3. Capture a live JVM Heap Dump (⚠️ Caution: STW pause on large heaps!)
jcmd <PID> GC.heap_dump /tmp/heap_dump.hprof

# 4. Native Memory Tracking (NMT)
# (Requires JVM startup flag: -XX:NativeMemoryTracking=detail)
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory detail.diff

# 5. Inspect Classloader & Metaspace Statistics
jcmd <PID> VM.classloader_stats
jcmd <PID> GC.class_histogram | head -n 30

# 6. Force GC and Print GC Heap Summary
jcmd <PID> GC.heap_info
```

---

### 2. Linux OS & Infrastructure Performance

```bash
# 1. High CPU Thread Profiling (Find Thread ID consuming CPU)
top -H -p <PID>
# Convert Linux TID (e.g. 4192) to Hex (e.g. 0x1060) to find in thread dump:
printf "0x%x\n" <TID>

# 2. System Memory & Swap Utilization
free -h
vmstat 1 10

# 3. Disk I/O Bottlenecks
iostat -xz 1 5
df -h
du -sh /tmp/* | sort -rh | head -n 10

# 4. Kernel OOM Invocations & Hardware Errors
dmesg -T | grep -i -E "oom-killer|out of memory|killed process"

# 5. NTP Clock Synchronization Status
timedatectl status
chronyc tracking
chronyc sources -v

# 6. Network Socket Inspection & TIME_WAIT Sockets
netstat -tulpn
ss -s
```

---

### 3. PostgreSQL Performance & Lock Queries

```sql
-- 1. Inspect Active Non-Idle Queries Running > 5 Seconds
SELECT pid, usename, client_addr, state, age(clock_timestamp(), query_start) AS duration, query 
FROM pg_stat_activity 
WHERE state != 'idle' AND age(clock_timestamp(), query_start) > interval '5 seconds'
ORDER BY duration DESC;

-- 2. Inspect Blocking Locks and Blocked Queries
SELECT blocked_locks.pid     AS blocked_pid,
       blocked_activity.query AS blocked_statement,
       blocking_locks.pid    AS blocking_pid,
       blocking_activity.query AS blocking_statement,
       blocked_locks.mode    AS blocked_lock_mode,
       blocking_locks.mode   AS blocking_lock_mode
FROM  pg_catalog.pg_locks         blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks         blocking_locks 
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.relation = blocked_locks.relation
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;

-- 3. Terminate a Blocked or Runaway Backend Query (⚠️ Terminates transaction)
SELECT pg_terminate_backend(<PID>);

-- 4. Check Table Bloat & Dead Tuples
SELECT relname, n_dead_tup, n_live_tup, round(n_dead_tup * 100.0 / (n_live_tup + n_dead_tup + 1), 2) AS dead_tuple_ratio
FROM pg_stat_user_tables
ORDER BY dead_tuple_ratio DESC LIMIT 10;
```

---

### 4. Apache Kafka CLI Operations

```bash
# 1. Describe Consumer Group Lag Across All Partitions
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group payments-service-group

# 2. Reset Consumer Group Offsets to Latest or Earliest (⚠️ Changes consumer position)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group payments-service-group \
  --reset-offsets --to-latest --execute --topic payment-events

# 3. Inspect Topic Partition Counts and Replication Factors
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic payment-events

# 4. Tail Live Messages from Dead Letter Topic (.DLT)
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic payment-events.DLT --from-beginning --max-messages 10
```

---

### 5. Redis CLI Diagnostics

```bash
# 1. Check Redis Latency Spikes
redis-cli --latency
redis-cli --latency-history

# 2. Inspect Slow Queries (SLOWLOG)
redis-cli SLOWLOG GET 10

# 3. Check Memory Breakdown and Key Count
redis-cli INFO memory
redis-cli INFO stats

# 4. Check Hot Keys / Memory Usage on Specific Key
redis-cli MEMORY USAGE <KEY_NAME>

# 5. Monitor Live Commands in Real-Time (⚠️ Performance hit under high throughput!)
redis-cli MONITOR
```

---

### 6. Kubernetes Operational Debugging

```bash
# 1. Find Pods with High Restart Counts or OOMKilled Status
kubectl get pods -n production --sort-by='.status.containerStatuses[0].restartCount'

# 2. Inspect Pod Termination Reason (OOMKilled vs Failed Probe)
kubectl describe pod <POD_NAME> -n production | grep -E "Terminated|Exit Code|Reason|Last State"

# 3. View Previous Container Crash Logs
kubectl logs <POD_NAME> -n production --previous --tail=100

# 4. Instant Rollback of a Broken Deployment
kubectl rollout undo deployment/finflow-api -n production

# 5. Check Ingress Controller Real-Time Access Logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx --tail=100 -f
```

---

*(End of Master Diagnostic Command Cheat Sheet)*
