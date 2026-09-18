# Cloud Resource Quota Manager

Enforces per-organization resource quotas with atomic reserve/release,
built as DigitalOcean live-interview practice.

## Run locally

```bash
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Auth (local/dev keys, seeded via Flyway V2)

- Admin key: `X-API-Key: admin-dev-key`
- Org key (acme): `X-API-Key: org-acme-dev-key`, orgId `11111111-1111-1111-1111-111111111111`

## Try it

```bash
curl -X POST http://localhost:8080/quotas/11111111-1111-1111-1111-111111111111/reserve \
  -H "X-API-Key: org-acme-dev-key" \
  -H "Content-Type: application/json" \
  -d '{"resourceType":"droplet","amount":1,"idempotencyKey":"demo-1"}'
```

## Test

```bash
mvn test
```

Includes `QuotaConcurrencyIT`, a Testcontainers-backed test proving 50
concurrent reserve calls never push usage past the configured limit.

## Observability

- **Structured logs** — JSON via Logback + `logstash-logback-encoder`. Every `reserve`/`release` call logs `orgId`, `resourceType`, `action`, `result` (`allowed`/`denied`/`idempotent_replay`), and `latencyMs` as queryable fields, not buried in a message string.
- **Metrics** — `quota_reserve_total{result=allowed|denied|idempotent_replay}` counter and a `quota_reserve_latency` timer (p50/p95/p99) via Micrometer, scraped at `/actuator/prometheus`.
- **Health** — `/actuator/health`, used as the App Platform health check.

```bash
curl http://localhost:8080/actuator/prometheus | grep quota_reserve
```

## Deploy to DigitalOcean App Platform

```bash
doctl apps create --spec .do/app.yaml
```

## Design

Problem definition, failure scenarios, NFRs, CAP trade-off, architecture
diagram, database rationale, and schema are captured in the accompanying
design doc (Phase 1 & 2).
