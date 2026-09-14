# MoveIQ

**Evidence-governed enterprise mobility intelligence built with Java and Spring Boot.**

MoveIQ turns the MoveInSync dataset and live mobility events into contextual operational signals, durable situations, evidence-backed decisions, governed actions, and measurable outcomes.

## Core stack

- **Java 17** with OOP, SOLID and concurrency-safe services
- **Spring Boot 3**, Spring MVC, Spring Data JPA, Hibernate, Validation, Actuator
- **PostgreSQL** for durable operational state and historical mobility data
- **Redis** for idempotency, low-latency state and caching
- **Kafka** for event-driven ingestion and distributed processing
- **Flyway** for versioned database migrations
- **Maven**, JUnit 5, Mockito, Testcontainers
- **Docker**, Kubernetes and AWS/EKS-ready deployment
- **OpenAPI/Swagger**, structured logging and health/metrics endpoints
- **React/Vite** frontend

## Architecture

```text
MoveInSync CSV / mobility events
            |
            v
 Validation + normalization
            |
            v
 PostgreSQL ---- Redis
      |            |
      +-----+------+ 
            v
  Kafka mobility-events
            |
            v
 deterministic detectors
 (sliding windows / thresholds)
            |
            v
 situation correlation
 + idempotent contributions
            |
            v
 evidence + decision layer
            |
            v
 human approval
            |
            v
 revalidation + idempotent action
            |
            v
 outcome verification
```

## Official MoveInSync dataset

Place the seven supplied files under `data/moveinsync/raw/`:

```text
emp_Data.csv
bill_data.csv
Ride_data_trip-may_2026.csv
Ride_data_trip-June_2026.csv
Ride_data_trip-July_2026.csv
trip_feedback.csv
alerts_data.csv
```

The raw files are intentionally Git-ignored because they are large. The folder contract and manifest remain versioned.

## Run locally

```bash
cp .env.example .env
docker compose up -d postgres redis kafka
make test
make backend
make frontend
```

Backend: `http://localhost:8080`  
Swagger: `http://localhost:8080/swagger-ui.html`  
Frontend: `http://localhost:5173`

## Engineering invariants

1. No metric without context.
2. No event processed twice.
3. No situation impact inflated by replay.
4. No action without fresh revalidation.
5. No unsupported numeric AI claim.
6. No claimed outcome without observation or an explicit estimate label.
