# Architecture — Vehicle Maintenance System

## Service Interaction Overview

```
Browser (UI :3000)
    │
    ├── POST /api/auth/signin|signup ──► Auth Service (:8080)
    │                                        │
    │                                        └── issues JWT (HS256, JWT_SECRET)
    │
    └── /api/v1/** + Bearer <JWT> ──────► Maintenance Service (:8081)
                                               │
                                               └── validates JWT locally (same JWT_SECRET)
                                               └── no call back to Auth Service at runtime
```

## JWT Flow

1. User logs in via UI → Auth Service validates credentials → returns signed JWT
2. UI stores JWT in browser storage
3. UI attaches `Authorization: Bearer <JWT>` on every Maintenance Service request
4. Maintenance Service validates the JWT signature using the shared `JWT_SECRET` — **no inter-service call**
5. Username and roles are extracted from JWT claims and used for authorization decisions

## Key Architecture Constraints

- **No shared database tables** — Auth Service owns `users`, `roles`, `user_roles`. Maintenance Service owns all domain tables. They run against the same MySQL instance but in separate schemas.
- **No inter-service HTTP calls at runtime** — JWT validation is local (shared secret). Services are fully decoupled at runtime.
- **JWT validation is duplicated by design** — The `AuthTokenFilter` and `JwtUtils` classes are intentionally copied into the Maintenance Service. This is the accepted stateless microservice pattern.
- **No password login in Maintenance Service** — It is a JWT resource server only. It never issues tokens or handles credentials.
- **Stateless sessions** — Neither backend uses HTTP sessions. Every request is independently authenticated via JWT.

## Environment Variables

Both backend services read configuration from environment variables — never from hard-coded values in source.

| Variable | Used by | Purpose |
|---|---|---|
| `JWT_SECRET` | user-auth-service, vehicle-maintenance-service | Shared HS256 signing key |
| `JWT_EXPIRATION_MS` | user-auth-service | Token TTL in milliseconds |
| `DB_HOST` | user-auth-service, vehicle-maintenance-service | MySQL hostname |
| `DB_PORT` | user-auth-service, vehicle-maintenance-service | MySQL port (default 3306) |
| `DB_NAME` | user-auth-service, vehicle-maintenance-service | Schema name (different per service) |
| `DB_USERNAME` | user-auth-service, vehicle-maintenance-service | MySQL user |
| `DB_PASSWORD` | user-auth-service, vehicle-maintenance-service | MySQL password |

## Docker Compose Service Names

Inside the Docker network, services reach each other by service name:

| Service name | Internal hostname | External port |
|---|---|---|
| `mysql` | `mysql` | 3306 |
| `user-auth-service` | `user-auth-service` | 8080 |
| `vehicle-maintenance-service` | `vehicle-maintenance-service` | 8081 |
| `vehicle-maintenance-ui` | `vehicle-maintenance-ui` | 3000 |

## Startup Order

```
mysql (health check: mysqladmin ping)
  └── user-auth-service             (depends_on: mysql, condition: service_healthy)
  └── vehicle-maintenance-service   (depends_on: mysql, condition: service_healthy)
        └── vehicle-maintenance-ui  (depends_on: user-auth-service, vehicle-maintenance-service)
```

## Database Schemas

**Auth Service schema** (`auth_db`)
- `roles` — id, name (ROLE_ADMIN, ROLE_TECHNICIAN, ROLE_CUSTOMER)
- `users` — id, username, email, password (bcrypt)
- `user_roles` — join table

**Maintenance Service schema** (`maintenance_db`)
- `vehicles` — id, vin, make, model, year, owner_username, deleted (soft delete)
- `technicians` — id, name, active
- `bays` — id, name, active
- `work_orders` — id, vehicle_id, technician_id, bay_id, status, description, created_at
- `work_order_status_history` — id, work_order_id, previous_status, new_status, changed_by, changed_at
- `part_lines` — id, work_order_id, part_name, quantity, unit_cost
- `labor_lines` — id, work_order_id, description, hours, rate
- `service_schedules` — id, vehicle_id, bay_id, scheduled_at (UTC), service_type, status
