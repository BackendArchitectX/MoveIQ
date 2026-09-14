# Performance and reliability

MoveIQ keeps the hot path bounded and observable:

- HikariCP connection pooling with explicit pool limits
- Hibernate JDBC batching and ordered writes
- Redis idempotency before expensive processing
- Kafka partitioning for horizontal consumer scale
- `ArrayDeque` sliding windows for O(1) amortized event eviction/insertion
- `ConcurrentHashMap` scope isolation for concurrent detector access
- composite PostgreSQL indexes for tenant/date/scope queries
- optimistic locking for concurrent situation/action updates
- transactional outbox to avoid database/Kafka dual-write loss
- Actuator + Prometheus metrics for latency, pool saturation and consumer health

For production performance investigations, run the JVM with Java Flight Recorder and correlate JFR findings with PostgreSQL query plans, Hikari metrics and Kafka consumer lag.
