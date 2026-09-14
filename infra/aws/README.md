# AWS deployment mapping

MoveIQ is designed so the local Docker stack has a direct managed-AWS equivalent.

| Local | AWS | Production responsibility |
|---|---|---|
| Spring Boot container | EKS | stateless API/consumer replicas, rolling deploys, HPA |
| PostgreSQL | RDS PostgreSQL Multi-AZ | durable operational state, Flyway migrations, backups |
| Redis | ElastiCache for Redis | distributed sliding windows / low-latency ephemeral state |
| Kafka | Amazon MSK | mobility-event and situation streams, consumer groups, DLT |
| `data/moveinsync/raw` | S3 | immutable source dataset |
| Prometheus/structured logs | CloudWatch + managed metrics | dashboards, alerting, incident triage |

## Network model

Run EKS worker nodes, RDS, ElastiCache and MSK in private subnets. Only the load balancer is public. Security groups allow application-to-database/cache/broker traffic on the required ports; databases and brokers receive no public IPs.

## Reliability

- at least 2 EKS replicas across AZs
- RDS Multi-AZ and automated backups
- Redis replication/failover where supported
- MSK multi-AZ brokers with replication factor >= 3 in production
- PodDisruptionBudget + HPA
- graceful Spring shutdown so Kafka consumers leave the group cleanly
- CloudWatch alarms on error rate, Kafka lag, DB pool saturation and outbox backlog

## Cost-conscious demo

For local/hackathon use, keep PostgreSQL/Redis/Kafka in Docker Compose. Do not provision EKS/MSK simply to prove familiarity; the production mapping is explicit and the application uses the same protocols and failure model.
