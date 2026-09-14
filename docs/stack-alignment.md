# Technical stack

| Area | MoveIQ |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot, Spring MVC |
| Persistence | Spring Data JPA, Hibernate |
| SQL | PostgreSQL, Flyway |
| Cache / idempotency | Redis |
| Messaging | Apache Kafka |
| Build | Maven |
| Testing | JUnit 5, Mockito, Testcontainers |
| API docs | OpenAPI / Swagger |
| Observability | Spring Actuator, Micrometer, Prometheus |
| Containers | Docker |
| Orchestration | Kubernetes |
| Cloud target | AWS EKS + RDS + ElastiCache + MSK |
| Frontend | React / Vite |

The backend intentionally demonstrates OOP/SOLID boundaries, optimistic locking, idempotent processing, event-driven design, transaction boundaries, bounded-memory sliding-window algorithms, and production-oriented observability.
