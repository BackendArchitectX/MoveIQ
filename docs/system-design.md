# MoveIQ system design

## Design objective

The project is intentionally built to answer the backend-interview question: **what happens when the same event is delivered twice while several application replicas are running?**

## Event path

```text
Producer/API
   │
   ▼
Kafka mobility topic
   │  at-least-once delivery
   ▼
MobilityEventConsumer
   │
   ▼
PostgreSQL processed_event claim
   │ duplicate? -> stop
   ▼
Redis ZSET sliding window
   │
   ▼
DetectedSignal
   │
   ▼
PostgreSQL situation UPSERT
   │
   ▼
contribution INSERT ... ON CONFLICT DO NOTHING
   │
   ├── duplicate contribution -> no impact mutation
   ▼
optimistic-locked situation aggregate
   │
   ▼
transactional outbox
   │
   ▼
Kafka situation topic
```

## Why PostgreSQL and Redis have different jobs

PostgreSQL owns durable truth: trips, processed-event claims, situations, contributions, action proposals, executions and outbox rows. Redis owns ephemeral distributed computation: the current sliding event window. If Redis loses the window, MoveIQ may temporarily under-detect until it rebuilds; it does not lose durable business state.

## Sliding-window complexity

Each non-safety event performs:

- `ZADD`: O(log N)
- `ZREMRANGEBYSCORE`: O(log N + M) where M is expired items removed
- `ZCARD`: O(1)

The key is scoped by business unit, office, shift, direction and event type, which bounds N and naturally distributes keys.

## Idempotency boundaries

1. Kafka consumer: `(consumer_group, business_unit, event_id)` primary key.
2. Redis window: `event_id` is the ZSET member.
3. Situation contribution: `(situation_id, source_event_id)` unique constraint.
4. Action execution: unique idempotency key and unique proposal ID.
5. Outbox: durable row remains unpublished until Kafka acknowledgement succeeds.

This is deliberately layered. A single Redis lock is not treated as exactly-once processing.

## Concurrency control

Situation rows use Hibernate optimistic locking (`@Version`) for ordinary concurrent updates. Situation creation uses PostgreSQL `ON CONFLICT`, eliminating the classic read-then-insert race. Action approval uses a pessimistic row lock because two managers must not execute the same proposal concurrently.

## Kafka failure strategy

The consumer uses exponential retry (500 ms -> 1 s -> 2 s -> 4 s -> 8 s) and then publishes the poison record to the original topic plus `.DLT`. The normal consumer group can continue processing later records.

## Transactional outbox

Database state and the outbox row commit together. Multiple publisher pods call a `FOR UPDATE SKIP LOCKED` query, so each batch is claimed by only one transaction at a time. This avoids the database/Kafka dual-write problem for situation publication.

## Action safety

The client never supplies evidence hashes. MoveIQ computes a SHA-256 digest from the persisted situation and sorted source contributions when the proposal is created. On approval it locks the proposal and computes the digest again. If anything changed, execution is rejected as stale.

The action adapter receives the request idempotency key. A real downstream integration must enforce that key too; the simulated adapter returns a deterministic external reference to model this contract.

## Data ingestion

The large competition files are streamed with Apache Commons CSV; they are never read fully into heap. Trip ingestion uses bounded batches and PostgreSQL upserts. Date parsing accepts the known source-format drift, and comma-formatted numeric IDs are normalized at the boundary.

JPA/Hibernate is used for transactional operational aggregates; bulk CSV ingestion uses `JdbcTemplate` because ORM-per-row inserts are the wrong abstraction for millions of records.

## AWS mapping

- Spring Boot pods -> EKS
- PostgreSQL -> RDS PostgreSQL / Multi-AZ
- Redis -> ElastiCache Redis
- Kafka -> MSK
- raw dataset -> S3
- application metrics/logs -> CloudWatch / Prometheus-compatible monitoring

This keeps the local architecture equivalent to the production architecture without requiring cloud resources for development.
