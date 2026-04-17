# Autocare Platform

Multi-service vehicle maintenance management system.

## Projects

| Project | Stack | Port |
|---|---|---|
| `user-auth-service` | Spring Boot 3.x, MySQL | 8080 |
| `vehicle-maintenance-service` | Spring Boot 3.x, MySQL | 8081 |
| `vehicle-maintenance-ui` | React + TypeScript + Vite | 3000 |
| `vehicle-parts-service` | Planned | — |
| `vehicle-financing-service` | Planned | — |
| `vehicle-safety-service` | Planned | — |

## Quick Start (Docker Compose)

```bash
cd autocare
cp .env.example .env
# Edit .env — set JWT_SECRET and DB_PASSWORD
docker compose up --build
```

Services start in order: MySQL → auth + maintenance (parallel) → UI.

| URL | Description |
|---|---|
| http://localhost:3000 | UI |
| http://localhost:8080/api/auth/signin | Auth Service |
| http://localhost:8081/swagger-ui.html | Maintenance Service API docs |
| http://localhost:8081/v3/api-docs | OpenAPI spec |

## Build Individually

```bash
# Auth Service
cd user-auth-service
./mvnw clean package -DskipTests

# Maintenance Service
cd vehicle-maintenance-service
./mvnw clean package -DskipTests

# UI
cd vehicle-maintenance-ui
npm install
npm run build
```

## Environment Variables

See `.env.example` for all required variables. The `JWT_SECRET` must be identical in both backend services.

## Database

Bootstrap SQL scripts are in `db/` and are automatically executed by MySQL on first start:
- `db/user-auth-service/init.sql` — auth schema + role seed
- `db/vehicle-maintenance-service/init.sql` — domain schema + bay/technician seed

Scripts are idempotent (`CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE`) — safe to re-run.
