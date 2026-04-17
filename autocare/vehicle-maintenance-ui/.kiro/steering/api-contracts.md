# API Contracts — Vehicle Maintenance System

## Auth Service (port 8080)

Base URL: `http://localhost:8080` (local) / `http://user-auth-service:8080` (Docker network)

No `Authorization` header required on these endpoints.

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/api/auth/signup` | Register a new user | 200 |
| POST | `/api/auth/signin` | Login, returns JWT + user info | 200 |

### Sign-in Response Shape
```json
{
  "token": "<JWT>",
  "type": "Bearer",
  "id": 1,
  "username": "john",
  "email": "john@example.com",
  "roles": ["ROLE_ADMIN"]
}
```

### Sign-up Request Shape
```json
{
  "username": "john",
  "email": "john@example.com",
  "password": "secret",
  "role": ["admin"]
}
```

---

## Maintenance Service (port 8081)

Base URL: `http://localhost:8081` (local) / `http://vehicle-maintenance-service:8081` (Docker network)

All endpoints require `Authorization: Bearer <JWT>` except `/actuator/health`.

### Vehicles — `/api/v1/vehicles`

| Method | Path | Description | Auth | Success |
|---|---|---|---|---|
| GET | `/api/v1/vehicles` | Paginated vehicle list | Any role | 200 |
| POST | `/api/v1/vehicles` | Create vehicle | ADMIN, TECHNICIAN | 201 |
| GET | `/api/v1/vehicles/{id}` | Get vehicle by id | Any role | 200 |
| PUT | `/api/v1/vehicles/{id}` | Update vehicle | ADMIN, TECHNICIAN | 200 |
| DELETE | `/api/v1/vehicles/{id}` | Soft-delete vehicle | ADMIN | 204 |

VIN must be exactly 17 alphanumeric characters → 400 if invalid. Duplicate VIN → 409.
ROLE_CUSTOMER sees only their own vehicles.

### Work Orders — `/api/v1/work-orders`

| Method | Path | Description | Auth | Success |
|---|---|---|---|---|
| GET | `/api/v1/work-orders` | Paginated + filtered list | Any role | 200 |
| POST | `/api/v1/work-orders` | Create work order (status: OPEN) | ADMIN, TECHNICIAN | 201 |
| GET | `/api/v1/work-orders/{id}` | Full work order with lines | Any role | 200 |
| PATCH | `/api/v1/work-orders/{id}/status` | Transition status | ADMIN, TECHNICIAN | 200 |
| PUT | `/api/v1/work-orders/{id}/assign` | Assign technician + bay | ADMIN | 200 |

Status state machine: `OPEN → IN_PROGRESS → PENDING_PARTS → IN_PROGRESS → COMPLETED → INVOICED`
Invalid transition → 422. ROLE_TECHNICIAN can only write to their own assigned work orders.

### Parts & Labor

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/api/v1/work-orders/{id}/parts` | Add part line | 201 |
| DELETE | `/api/v1/work-orders/{id}/parts/{partLineId}` | Remove part line | 204 |
| POST | `/api/v1/work-orders/{id}/labor` | Add labor line | 201 |
| DELETE | `/api/v1/work-orders/{id}/labor/{laborLineId}` | Remove labor line | 204 |

Modification on COMPLETED or INVOICED work order → 422.

### Schedules — `/api/v1/schedules`

| Method | Path | Description | Success |
|---|---|---|---|
| GET | `/api/v1/schedules` | Paginated list, optional date range filter | 200 |
| GET | `/api/v1/schedules?vehicleId={id}` | Schedules for a vehicle | 200 |
| POST | `/api/v1/schedules` | Create schedule | 201 |
| DELETE | `/api/v1/schedules/{id}` | Cancel schedule | 204 |

Bay conflict (within 2 hours) → 409. All date-times in UTC, ISO 8601 format.

### Technicians — `/api/v1/technicians`

| Method | Path | Description | Auth | Success |
|---|---|---|---|---|
| GET | `/api/v1/technicians` | List active technicians with WO count | Any role | 200 |
| POST | `/api/v1/technicians` | Create technician | ADMIN | 201 |
| PUT | `/api/v1/technicians/{id}` | Update technician | ADMIN | 200 |
| DELETE | `/api/v1/technicians/{id}` | Deactivate technician | ADMIN | 204 |

### Bays — `/api/v1/bays`

| Method | Path | Description | Auth | Success |
|---|---|---|---|---|
| GET | `/api/v1/bays` | List bays with availability | Any role | 200 |
| POST | `/api/v1/bays` | Create bay | ADMIN | 201 |
| PUT | `/api/v1/bays/{id}` | Update bay | ADMIN | 200 |
| DELETE | `/api/v1/bays/{id}` | Deactivate bay | ADMIN | 204 |

### OpenAPI / Swagger

| Path | Description |
|---|---|
| `/v3/api-docs` | OpenAPI 3.x JSON spec |
| `/swagger-ui.html` | Interactive Swagger UI |

---

## Common HTTP Status Codes

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Validation error (bad input) |
| 401 | Unauthenticated (missing/invalid/expired JWT) |
| 403 | Forbidden (authenticated but insufficient role) |
| 404 | Resource not found |
| 409 | Conflict (duplicate VIN, bay conflict) |
| 422 | Unprocessable (invalid state transition, closed work order modification) |

---

## UI API Client Configuration

The UI uses Axios with a base instance per backend:

- `authClient` — base URL from `VITE_AUTH_API_URL` (default `http://localhost:8080`)
- `maintenanceClient` — base URL from `VITE_MAINTENANCE_API_URL` (default `http://localhost:8081`), Axios interceptor attaches `Authorization: Bearer <JWT>` automatically on every request
