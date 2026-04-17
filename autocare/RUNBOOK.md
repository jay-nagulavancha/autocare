# Autocare Platform — Runbook

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [First-Time Setup](#first-time-setup)
4. [Running the Full Stack](#running-the-full-stack)
5. [Running Individual Services](#running-individual-services)
6. [Building for Production](#building-for-production)
7. [Testing](#testing)
8. [Seeding Test Data](#seeding-test-data)
9. [Common Operations](#common-operations)
10. [Troubleshooting](#troubleshooting)
11. [Environment Variables Reference](#environment-variables-reference)
12. [API Quick Reference](#api-quick-reference)

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Docker Desktop | 20.x+ | https://www.docker.com/products/docker-desktop |
| Docker Compose | v2.x+ | Included with Docker Desktop |
| Java JDK | 11 or 17 | https://adoptium.net |
| Maven | 3.8+ | Bundled via `mvnw` — no install needed |
| Node.js | 18+ | https://nodejs.org |
| npm | 9+ | Bundled with Node.js |
| Newman (optional) | latest | `npm install -g newman` |

Verify your setup:
```bash
docker --version
docker compose version
java -version
node --version
npm --version
```

---

## Project Structure

```
autocare/
├── user-auth-service/           # Spring Boot — identity & JWT (port 8080)
├── vehicle-maintenance-service/ # Spring Boot — domain API (port 8081)
├── vehicle-maintenance-ui/      # React + Vite — frontend (port 3000)
├── vehicle-parts-service/       # Planned
├── vehicle-financing-service/   # Planned
├── vehicle-safety-service/      # Planned
├── db/
│   ├── user-auth-service/
│   │   └── init.sql             # Auth schema + role seed (idempotent)
│   └── vehicle-maintenance-service/
│       └── init.sql             # Domain schema + bay/technician seed (idempotent)
├── tests/
│   └── postman/
│       ├── autocare.postman_collection.json
│       ├── autocare.postman_environment.json
│       └── README.md
├── docker-compose.yml
├── .env.example
├── .env                         # Local secrets — never commit
└── RUNBOOK.md                   # This file
```

---

## First-Time Setup

### 1. Clone and enter the project
```bash
git clone <repo-url>
cd autocare
```

### 2. Create your `.env` file
```bash
cp .env.example .env
```

Edit `.env` and set:
```env
# Generate a strong secret: openssl rand -base64 64
JWT_SECRET=your_strong_base64_secret_here

# MySQL root password
DB_PASSWORD=your_strong_db_password_here

# Token TTL (default 24 hours)
JWT_EXPIRATION_MS=86400000
```

> **Never commit `.env` to source control.**

### 3. Start Docker Desktop
Make sure Docker Desktop is running before proceeding.

---

## Running the Full Stack

### Start all services
```bash
cd autocare
docker compose up --build
```

First run takes ~5–10 minutes (Maven downloads dependencies, npm installs packages). Subsequent runs use Docker layer cache and start in ~30 seconds.

### Start in background (detached)
```bash
docker compose up -d --build
```

### Check status
```bash
docker compose ps
```

### View logs
```bash
# All services
docker compose logs -f

# Single service
docker compose logs -f user-auth-service
docker compose logs -f vehicle-maintenance-service
docker compose logs -f vehicle-maintenance-ui
docker compose logs -f mysql
```

### Stop all services
```bash
docker compose down
```

### Stop and wipe database (clean slate)
```bash
docker compose down -v
```

> Use `down -v` when you change the DB schema (e.g. after modifying `init.sql`).

---

## Running Individual Services

### user-auth-service (Spring Boot)
```bash
cd autocare/user-auth-service

# Build
./mvnw clean package -DskipTests

# Run locally (requires MySQL running)
export JWT_SECRET=your_secret
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=auth_db
export DB_USERNAME=root
export DB_PASSWORD=your_password
./mvnw spring-boot:run
```

### vehicle-maintenance-service (Spring Boot)
```bash
cd autocare/vehicle-maintenance-service

# Build
./mvnw clean package -DskipTests

# Run locally (requires MySQL running)
export JWT_SECRET=your_secret
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=maintenance_db
export DB_USERNAME=root
export DB_PASSWORD=your_password
./mvnw spring-boot:run
```

### vehicle-maintenance-ui (React + Vite)
```bash
cd autocare/vehicle-maintenance-ui

# Install dependencies
npm install

# Start dev server (hot reload)
npm run dev
# → http://localhost:3000

# Build for production
npm run build
```

---

## Building for Production

### Build all Docker images
```bash
cd autocare
docker compose build
```

### Build a single service image
```bash
docker compose build user-auth-service
docker compose build vehicle-maintenance-service
docker compose build vehicle-maintenance-ui
```

### Build Spring Boot JARs
```bash
# Auth service
cd autocare/user-auth-service
./mvnw clean package -DskipTests
# Output: target/user-auth-service-0.0.1-SNAPSHOT.jar

# Maintenance service
cd autocare/vehicle-maintenance-service
./mvnw clean package -DskipTests
# Output: target/vehicle-maintenance-service-0.0.1-SNAPSHOT.jar
```

### Build UI static assets
```bash
cd autocare/vehicle-maintenance-ui
npm run build
# Output: dist/
```

---

## Testing

### Java Integration Tests — user-auth-service

Uses H2 in-memory database. Fully re-runnable. No running services required.

```bash
cd autocare/user-auth-service
./mvnw test
```

**Coverage:** 23 tests
- User registration (admin, technician, customer roles)
- Duplicate username/email rejection
- Sign-in with valid/invalid credentials
- JWT contains roles claim
- Validation errors

### Java Integration Tests — vehicle-maintenance-service

Uses H2 in-memory database. Fully re-runnable. No running services required.

```bash
cd autocare/vehicle-maintenance-service
./mvnw test
```

**Coverage:** 39 tests
- Vehicle CRUD, VIN validation, soft delete, customer isolation
- Work order lifecycle (create → assign → transition → full state machine)
- Status history recording
- Parts/labor add/remove, cost calculation, closed WO rejection
- Bay conflict detection (2-hour window)
- Schedule create/list/cancel
- Technician and bay management
- Role-based access (ADMIN, TECHNICIAN, CUSTOMER)

### Run all Java tests
```bash
cd autocare/user-auth-service && ./mvnw test
cd autocare/vehicle-maintenance-service && ./mvnw test
```

### Postman / Newman (black-box, against live stack)

Requires the full stack to be running (`docker compose up`).

```bash
# Install Newman CLI
npm install -g newman

# Run the collection
newman run autocare/tests/postman/autocare.postman_collection.json \
  -e autocare/tests/postman/autocare.postman_environment.json
```

Or import into Postman GUI:
1. Open Postman
2. Import `autocare/tests/postman/autocare.postman_collection.json`
3. Import `autocare/tests/postman/autocare.postman_environment.json`
4. Select "Autocare Local" environment
5. Click "Run collection"

**Coverage:** 29 tests covering the full end-to-end flow against the live stack.

**Re-runnability:** Each run generates a unique `RUN_ID` (timestamp-based). Test data never conflicts. Safe to run multiple times.

---

## Seeding Test Data

### Option 1 — Postman/Newman (recommended)
Runs all 29 API calls and leaves data in MySQL:
```bash
newman run autocare/tests/postman/autocare.postman_collection.json \
  -e autocare/tests/postman/autocare.postman_environment.json
```

After running, log into the UI with:
- Username: `admin_<RUN_ID>` (shown in Newman output)
- Password: `Admin@123`

### Option 2 — curl (manual)
```bash
# 1. Register admin user
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@autocare.com","password":"Admin@123","role":["admin"]}'

# 2. Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# 3. Create a vehicle
curl -X POST http://localhost:8081/api/v1/vehicles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"vin":"1HGBH41JXMN109186","make":"Honda","model":"Civic","year":2023,"ownerUsername":"admin"}'

# 4. Create a technician
curl -X POST http://localhost:8081/api/v1/technicians \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"John Smith"}'

# 5. Create a bay
curl -X POST http://localhost:8081/api/v1/bays \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Bay 1"}'
```

---

## Common Operations

### Re-register admin after DB wipe
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@autocare.com","password":"Admin@123","role":["admin"]}'
```

### Rebuild a single service after code change
```bash
cd autocare
docker compose build vehicle-maintenance-service
docker compose up -d vehicle-maintenance-service
```

### Rebuild UI after code change
```bash
cd autocare
docker compose build vehicle-maintenance-ui
docker compose up -d vehicle-maintenance-ui
```

### Rebuild everything
```bash
cd autocare
docker compose down
docker compose up --build
```

### Reset database (wipe all data)
```bash
cd autocare
docker compose down -v
docker compose up -d
```

### Check service health
```bash
curl http://localhost:8081/actuator/health
# → {"status":"UP"}
```

### View Swagger API docs
Open in browser: http://localhost:8081/swagger-ui.html

Authenticate in Swagger:
1. Click "Authorize" button
2. Login via `POST /api/auth/signin` to get a token
3. Enter `Bearer <token>` in the Authorize dialog

---

## Troubleshooting

### Port already in use
```bash
# Check what's using the port
lsof -i :8080
lsof -i :8081
lsof -i :3000
lsof -i :3306

# Kill the process or change the port in docker-compose.yml
```

### MySQL port 3306 conflict (local MySQL running)
The `docker-compose.yml` maps MySQL to host port `3307` to avoid conflicts.
Connect from host: `mysql -h 127.0.0.1 -P 3307 -u root -p`

### Services fail to start — "Unknown database"
The DB schema wasn't created. Wipe the volume and restart:
```bash
docker compose down -v && docker compose up -d
```

### CORS errors in browser
The maintenance service has CORS enabled for all origins in development.
If you see CORS errors, rebuild the maintenance service:
```bash
docker compose build vehicle-maintenance-service
docker compose up -d vehicle-maintenance-service
```

### UI shows blank page / TypeError
Usually a null reference in the UI. Hard-refresh the browser (`Cmd+Shift+R` on Mac).
If it persists, check the browser console for the exact error and file.

### JWT "Access Denied" on maintenance service endpoints
Your token may be missing the `roles` claim (old token from before the fix).
Log out and log back in to get a fresh token with roles embedded.

### Maven build fails — "Could not resolve dependencies"
```bash
# Clear Maven cache and retry
rm -rf ~/.m2/repository
./mvnw clean package -DskipTests
```

### Docker build fails — "no space left on device"
```bash
# Clean up unused Docker resources
docker system prune -a
```

---

## Environment Variables Reference

| Variable | Service | Required | Default | Description |
|---|---|---|---|---|
| `JWT_SECRET` | auth, maintenance | Yes | — | Base64-encoded HS256 signing key. Must be identical in both services. Generate: `openssl rand -base64 64` |
| `JWT_EXPIRATION_MS` | auth | No | `86400000` | Token TTL in milliseconds (default 24h) |
| `DB_HOST` | auth, maintenance | No | `localhost` | MySQL hostname |
| `DB_PORT` | auth, maintenance | No | `3306` | MySQL port |
| `DB_NAME` | auth, maintenance | No | `auth_db` / `maintenance_db` | Schema name |
| `DB_USERNAME` | auth, maintenance | No | `root` | MySQL user |
| `DB_PASSWORD` | auth, maintenance | Yes | — | MySQL password |
| `VITE_AUTH_API_URL` | ui | No | `http://localhost:8080` | Auth service base URL (baked in at build time) |
| `VITE_MAINTENANCE_API_URL` | ui | No | `http://localhost:8081` | Maintenance service base URL (baked in at build time) |

---

## API Quick Reference

### Auth Service — http://localhost:8080

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | None | Register user. Body: `{username, email, password, role[]}` |
| POST | `/api/auth/signin` | None | Login. Returns `{accessToken, tokenType, id, username, email, roles}` |

### Maintenance Service — http://localhost:8081

All endpoints require `Authorization: Bearer <token>` except `/actuator/health`.

| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/api/v1/vehicles` | Any | List vehicles (paginated) |
| POST | `/api/v1/vehicles` | ADMIN, TECH | Create vehicle |
| GET | `/api/v1/vehicles/{id}` | Any | Get vehicle |
| PUT | `/api/v1/vehicles/{id}` | ADMIN, TECH | Update vehicle |
| DELETE | `/api/v1/vehicles/{id}` | ADMIN | Soft-delete vehicle |
| GET | `/api/v1/work-orders` | Any | List work orders (filterable by status, vehicleId, technicianId) |
| POST | `/api/v1/work-orders` | ADMIN, TECH | Create work order |
| GET | `/api/v1/work-orders/{id}` | Any | Get work order with parts, labor, history |
| PATCH | `/api/v1/work-orders/{id}/status` | ADMIN, TECH | Transition status |
| PUT | `/api/v1/work-orders/{id}/assign` | ADMIN | Assign technician + bay |
| POST | `/api/v1/work-orders/{id}/parts` | ADMIN, TECH | Add part line |
| DELETE | `/api/v1/work-orders/{id}/parts/{partLineId}` | ADMIN, TECH | Remove part line |
| POST | `/api/v1/work-orders/{id}/labor` | ADMIN, TECH | Add labor line |
| DELETE | `/api/v1/work-orders/{id}/labor/{laborLineId}` | ADMIN, TECH | Remove labor line |
| GET | `/api/v1/schedules` | Any | List schedules |
| POST | `/api/v1/schedules` | Any | Create schedule |
| DELETE | `/api/v1/schedules/{id}` | Any | Cancel schedule |
| GET | `/api/v1/technicians` | Any | List technicians |
| POST | `/api/v1/technicians` | ADMIN | Create technician |
| DELETE | `/api/v1/technicians/{id}` | ADMIN | Deactivate technician |
| GET | `/api/v1/bays` | Any | List bays |
| POST | `/api/v1/bays` | ADMIN | Create bay |
| DELETE | `/api/v1/bays/{id}` | ADMIN | Deactivate bay |
| GET | `/v3/api-docs` | None | OpenAPI spec |
| GET | `/swagger-ui.html` | None | Swagger UI |
| GET | `/actuator/health` | None | Health check |

### Work Order State Machine

```
OPEN → IN_PROGRESS → PENDING_PARTS → IN_PROGRESS → COMPLETED → INVOICED
```

- `OPEN → IN_PROGRESS` ✅
- `IN_PROGRESS → PENDING_PARTS` ✅
- `IN_PROGRESS → COMPLETED` ✅ (skip PENDING_PARTS if no parts needed)
- `PENDING_PARTS → IN_PROGRESS` ✅
- `COMPLETED → INVOICED` ✅
- Any other transition → HTTP 422

### User Roles

| Role | Access |
|---|---|
| `ROLE_ADMIN` | Full access to all operations |
| `ROLE_TECHNICIAN` | Read all, write only to assigned work orders |
| `ROLE_CUSTOMER` | Read only own vehicles and work orders |
