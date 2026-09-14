# MoveIQ architecture

MoveIQ is a Java/Spring Boot distributed backend organized around **data**, **intelligence**, and **control** planes.

## Data plane

The official MoveInSync files are validated and streamed into PostgreSQL using bounded batches. Source identifiers are normalized once, while raw values remain auditable. Redis holds short-lived idempotency and hot-state keys.

## Intelligence plane

Kafka carries mobility events. Deterministic detectors use bounded sliding windows and business thresholds before creating situations. A situation is updated through unique source contributions, so replaying an event cannot inflate impact.

## Control plane

Actions are proposals first. Approval is followed by fresh evidence revalidation. Execution uses an idempotency key and produces a durable receipt. This separates `recommended`, `approved`, `executed`, and `verified` states.

## Distributed-system guarantees

- Kafka consumer groups for horizontal scale
- Redis `SET NX` event idempotency
- PostgreSQL optimistic locking with `@Version`
- transactional outbox for database-to-Kafka reliability
- unique situation contributions for replay safety
- retry/DLQ-ready Kafka topology
- stateless Spring Boot API instances suitable for Kubernetes/EKS
