# MoveIQ

**Distributed mobility operations intelligence built as a Java backend portfolio project.**

MoveIQ ingests the official MoveInSync dataset, accepts/replays mobility events, detects material operational patterns, correlates them into durable situations, and executes approved actions with stale-evidence and idempotency safeguards.

## Why this project exists

The codebase is intentionally designed around backend engineering fundamentals rather than framework collection:

```text
MoveInSync CSVs
      │
      ├── streaming profile / batch trip ingestion
      ▼
 PostgreSQL (RDS-ready)
      │
REST ─┼─> Kafka ─> transactional event processor
      │                │
      │                ├─> PostgreSQL processed-event claim
      │                ├─> Redis distributed sliding window
      │                ├─> Situation + immutable contribution
      │                └─> Transactional outbox
      │
      └─> action proposal -> evidence recompute -> approval
                              -> idempotent action executor
```

## Backend stack

- Java 17
- Spring Boot 3 / Spring MVC
- Spring Data JPA + Hibernate
- PostgreSQL + Flyway
- Redis
- Apache Kafka
- Maven
- JUnit 5 + Mockito
- OpenAPI / Swagger
- Actuator + Micrometer + Prometheus
- Docker + Kubernetes
- Jenkins + GitHub Actions

Java 17 is used because it is a modern Java 8+ LTS runtime and enables Spring Boot 3 while retaining the Java/OOP/concurrency fundamentals expected in backend interviews.

## Distributed-system guarantees

### Duplicate Kafka delivery
`processed_event` is claimed with PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` inside the same transaction as situation mutation. Redis uses the event ID as the sorted-set member, so retries do not inflate the sliding window.

### Horizontal scaling
The detection window is stored in Redis ZSETs rather than JVM memory. All application replicas therefore observe the same event window.

### Situation race safety
Situation creation uses PostgreSQL upsert semantics and contributions use a unique `(situation_id, source_event_id)` invariant. Replaying one event 100 times cannot multiply impact.

### Reliable publication
Situation changes write an outbox row in the same database transaction. Publishers use `FOR UPDATE SKIP LOCKED` so multiple pods can drain the outbox without claiming the same row concurrently.

### Safe actions
Clients cannot provide their own evidence hash. MoveIQ computes it from persisted situation state and contributions. At approval time it recomputes the hash under a row lock; stale recommendations are rejected. The executor receives an idempotency key that must also be honored downstream.

### Poison messages
Kafka retries with exponential backoff and routes exhausted records to a `.DLT` topic.

## Dataset

Keep the seven large MoveInSync files locally under:

```text
data/moveinsync/raw/
├── emp_Data.csv
├── bill_data.csv
├── Ride_data_trip-may_2026.csv
├── Ride_data_trip-June_2026.csv
├── Ride_data_trip-July_2026.csv
├── trip_feedback.csv
└── alerts_data.csv
```

The source files stay Git-ignored. `GET /api/v1/data/status` validates placement, `GET /api/v1/data/profile` performs streaming quality profiling, and `POST /api/v1/data/ingest/trips` streams the three trip files into canonical PostgreSQL rows in bounded batches.

## Run locally

```bash
make infra
make backend
make frontend
```

Or:

```bash
make demo
```

- Backend: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Actuator: `http://localhost:8080/actuator`
- Frontend: `http://localhost:5173`

## Useful endpoints

```text
GET  /api/v1/data/status
GET  /api/v1/data/profile?maxRowsPerFile=100000
POST /api/v1/data/ingest/trips
POST /api/v1/events
GET  /api/v1/situations
POST /api/v1/situations/{id}/actions
POST /api/v1/actions/{proposalId}/approve
```

## AWS production mapping

The local stack maps cleanly to EKS + RDS PostgreSQL + ElastiCache Redis + MSK Kafka + CloudWatch. See `infra/aws/README.md` and `docs/system-design.md`.

## Engineering rule

> No metric without context. No duplicate without an idempotency boundary. No event side effect without durable state. No action without fresh evidence.
