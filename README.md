# schema-version-control

Git-like version control for database schemas — branch, diff, merge, and resolve schema conflicts.

Model a database schema as a versioned document, evolve it on branches, and get it into `main`
only through a peer-reviewed merge request. Merges are genuine three-way merges against the
common ancestor, so two people editing the same table are told exactly what disagrees and asked
to decide, rather than one silently overwriting the other.

---

## What it does

| | |
|---|---|
| **Repositories** | Each targets a database engine (PostgreSQL, MySQL, SQLite, Oracle, or generic ANSI SQL) and validates column types against that engine's catalogue. |
| **Branches** | Fork from any branch, edit tables, columns, constraints and indexes, commit versions. |
| **Protected `main`** | Direct commits are rejected. `main` moves only through an approved merge request. |
| **Merge requests** | Side-by-side, ancestor-aware diff. Each pane shows what *that* branch changed since the fork. |
| **Peer approval** | You cannot approve your own request. Approval is bound to the exact branch heads reviewed, so any new commit makes it stale and it must be re-approved. |
| **Three-way merge** | Finds the common ancestor and merges against it. Non-conflicting divergence merges silently. |
| **Conflict resolution** | Real conflicts are listed per path with both definitions, and settled by choosing a side. A merge can only land a definition that already exists on one of the two branches. |
| **Audit trail** | Every commit, branch, approval, invalidation and merge is recorded, including which side was chosen for each conflict. |

Architecture decisions, alternatives weighed and defects found along the way are recorded in
[`decisions.md`](decisions.md).

---

## Stack

- **Backend** — Java 17, Spring Boot 3.3, Spring Security (stateless JWT), Spring Data JPA, Flyway
- **Database** — PostgreSQL 16 (H2 in tests only)
- **Frontend** — React 18, Vite, `lucide-react`, hand-written CSS — no UI framework
- **Deployment** — Docker, Render blueprint

---

## Quick start

Requires Docker and Node 18+.

```bash
# Backend + Postgres
docker compose up --build          # http://localhost:8080

# Frontend, in a second terminal
cd frontend && npm install && npm run dev    # http://localhost:3000
```

Open http://localhost:3000 and register an account — the first one is created through the UI, and
nothing needs seeding for the app to work.

For demo data (two users, three repositories, a merge request already approved and ready to merge):

```bash
python3 scripts/seed_demo.py
# jay@example.com / password
# alex@example.com / password
```

### Running the backend from your IDE

```bash
docker compose up -d db      # Postgres only, published on 55432
```

Then run `SchemaVersionControlApplication`. The defaults in `application.yml` already point at that
database, so no environment variables are needed.

> Postgres is published on **55432**, not 5432, so it cannot collide with a Postgres already
> installed on your machine. Inside the compose network it is still `db:5432`.

---

## Configuration

Every value that differs between machines or deployments is an environment variable, so the same
image runs locally and in production with only the environment changing.

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8080` | Render injects this. |
| `DB_HOST` | `localhost` | `db` inside compose. |
| `DB_PORT` | `55432` | The port compose publishes. |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `schemavc` | |
| `DB_POOL_MAX` | `5` | Kept low; free Postgres plans allow few connections. |
| `JWT_SECRET` | development value | **Must be replaced in any deployment.** At least 32 characters. |
| `JWT_EXPIRATION_MS` | `86400000` | 24 hours. |
| `CORS_ALLOWED_ORIGINS` | `*` | Comma-separated. Set to the frontend origin in production. |
| `SPRING_PROFILES_ACTIVE` | — | `prod` in deployments. |
| `JPA_SHOW_SQL` / `LOG_LEVEL` | `false` / `INFO` | |

Frontend (`frontend/.env`, see `.env.example`):

| Variable | Notes |
|---|---|
| `VITE_API_BASE_URL` | Leave empty locally so Vite's dev proxy forwards `/api` to :8080. In a deployment, the backend's origin. Inlined at **build** time, so changing it requires a rebuild. |

On the `prod` profile the application refuses to start if `JWT_SECRET` is still the development
default, and warns if CORS is left open — a misconfigured deploy fails loudly instead of running
quietly insecure.

---

## Database and migrations

Flyway owns the schema; Hibernate runs with `ddl-auto: validate` and only checks that the entity
mappings still agree with it. Migrations live in `src/main/resources/db/migration`.

`V1__init.sql` was produced by letting Hibernate build the schema against Postgres 16, dumping the
result, then naming the constraints and adding the indexes Hibernate does not infer — Postgres does
not index foreign keys automatically.

To change the schema, add `V2__describe_change.sql` and update the entities to match. Never edit an
applied migration; Flyway checksums them.

**There is no seed migration, deliberately.** Nothing is required for the application to function —
`main` and its initial version are created at runtime, and the engine type catalogues are served
from code rather than tables. Demo data belongs in `scripts/seed_demo.py`, which is opt-in, can be
re-run, and refuses to point at a non-local host without an explicit password.

---

## Testing

```bash
mvn test          # 21 tests
```

Tests run against in-memory H2 with Flyway disabled, so the suite needs no Docker and finishes in
seconds. Coverage is concentrated where the logic is genuinely hard:

- `MergeConflictResolutionTest` — every conflict type resolvable in both directions, stable conflict
  keys, mixed sides, partial resolutions still blocking, unrecognised choices ignored
- `ConflictResolutionFlowTest` — the merge request flow end to end, including that resolutions
  cannot bypass the approval gate
- `ApprovalFlowIntegrationTest` — protected `main`, self-approval refusal, stale approval detection

---

## Deploying to Render

The repository contains a [`render.yaml`](render.yaml) blueprint that provisions all three pieces:
a Postgres database, the API as a Docker web service, and the frontend as a static site.

1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint** and select the repository.
3. Apply the blueprint. `JWT_SECRET` is generated automatically, and the database credentials and
   the frontend's API URL are wired between services.

**Free tier caveats — both of these bite in practice:**

- A free Postgres instance is **deleted roughly 30 days after creation**. Move it to a paid plan or
  back it up if the data matters.
- Free web services **sleep after 15 minutes idle**. The next request waits around 50 seconds for a
  JVM cold start, which reads as the app being broken. The frontend is a static site and stays up,
  so the symptom is a page that loads and then cannot reach its API.

After the first deploy, confirm `CORS_ALLOWED_ORIGINS` on the API matches the frontend's real
origin — including a custom domain if you attach one.

---

## Project structure

```
src/main/java/com/schema/versioncontrol/
  controller/   REST endpoints
  service/      interfaces; impl/ holds the logic, including ThreeWayMergeEngine
  repository/   Spring Data JPA
  model/        JPA entities
  dto/          request/response objects
  config/       security, JWT, deployment checks
src/main/resources/db/migration/   Flyway migrations

frontend/src/
  components/   ui/ primitives, then auth/ layout/ editor/ merge/
  state/        WorkspaceProvider (repos, branches, versions, MRs), ToastProvider
  lib/          api client, schema diff and data-type helpers
  styles/       tokens, base, ui primitives, app layout
```
