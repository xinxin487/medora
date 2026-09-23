# Local deployment

The development Compose stack keeps the original local ports and named volumes:

- PostgreSQL 16 + pgvector: `localhost:5432`
- Redis 7: `localhost:6379`
- MinIO API/console: `localhost:9000` / `localhost:9001`
- hospital-directory MCP: `localhost:18080/mcp`

Copy `.env.example` to `.env` and change local credentials if the defaults conflict with your machine. The placeholder `AI_CHAT_API_KEY=replace-me` is sufficient for infrastructure, tests, seed, and report generation. A real key is required only after selecting a real `AI_CHAT_PROVIDER` for chat/rerank demonstrations.

Load `.env` into the shell before starting the backend or running scripts so customized database and MCP values stay consistent:

```bash
set -a
source .env
set +a
```

```bash
docker compose -f deploy/compose/docker-compose.dev.yml up -d --build
docker compose -f deploy/compose/docker-compose.dev.yml ps
```

Start the backend once to let Flyway create the schema, then load the deterministic synthetic fixture:

```bash
./scripts/seed-demo.sh
./scripts/test-seed-idempotency.sh
```

With the backend and frontend running, verify all four infrastructure services, authentication, the knowledge fixture, and MCP discovery without printing response bodies or secrets:

```bash
./scripts/test-smoke-a-stage.sh
./scripts/smoke-a-stage.sh
```

The local-only disposable login is `demo.patient@medora.local` / `MedoraDemo!2026`. Never reuse it outside this demo. No real patient data is included.

Reset appointment/hold/waitlist state without deleting volumes or unrelated user data:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml exec -T postgres \
  psql -U postgres -d medora < scripts/reset-demo-data.sql
```

Stop services without deleting data:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml down
```

Named volumes are intentionally retained. Do not add `-v` unless you explicitly want to delete the local databases.
