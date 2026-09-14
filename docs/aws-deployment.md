# AWS deployment target

Recommended production mapping:

- Spring Boot API/consumers -> Amazon EKS
- PostgreSQL -> Amazon RDS for PostgreSQL
- Redis -> Amazon ElastiCache for Redis
- Kafka -> Amazon MSK
- raw dataset / generated reports -> Amazon S3
- secrets -> AWS Secrets Manager
- metrics/logs -> CloudWatch + Prometheus-compatible metrics

The application is stateless outside PostgreSQL/Redis/Kafka, so API and consumer replicas can scale independently with Kubernetes HPA.
