# API surface

- `GET /actuator/health` — application health
- `GET /api/v1/data/status` — checks the seven official MoveInSync files
- `POST /api/v1/events` — accepts a mobility event and publishes it to Kafka
- `GET /api/v1/situations` — lists durable correlated situations
- `GET /api/v1/situations/{id}` — retrieves one situation
- `POST /api/v1/situations/{id}/actions` — creates an action proposal
- `POST /api/v1/actions/{proposalId}/approve` — revalidates evidence hash and executes idempotently

Swagger UI is available at `/swagger-ui.html`.
