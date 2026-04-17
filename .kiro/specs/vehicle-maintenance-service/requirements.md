# Requirements Document — Maintenance Service

## Introduction

The Maintenance Service is a new Spring Boot 3.x backend that owns all vehicle maintenance business logic: vehicles (VIN), work orders, service schedules, parts/labor lines, technicians, bays, and status history. It validates JWTs issued by the Auth Service but performs no password-based authentication itself.

**Stack:** Spring Boot 3.x, Spring Web, Spring Validation, Spring Data JPA, Spring Security (JWT resource server), MySQL, springdoc-openapi.  
**API style:** REST under `/api/v1/`.  
**Port:** 8081.

---

## Glossary

- **Domain_Service**: This application — responsible for all vehicle maintenance business logic.
- **JWT**: JSON Web Token issued by the Auth Service and validated here.
- **JWT_Secret**: A shared symmetric key (HS256) configured via environment variable; must match the Auth Service secret.
- **VIN**: Vehicle Identification Number — a 17-character alphanumeric identifier unique to each vehicle.
- **Work_Order**: A record representing a maintenance job for a specific vehicle, including status, assigned technician, bay, parts, and labor lines.
- **Service_Schedule**: A planned future maintenance event associated with a vehicle and optionally a Work_Order.
- **Technician**: A shop technician who can be assigned to Work_Orders.
- **Bay**: A physical service bay that can be assigned to a Work_Order or Service_Schedule.
- **Part_Line**: A line item on a Work_Order representing a part used, with quantity and unit cost.
- **Labor_Line**: A line item on a Work_Order representing labor performed, with hours and rate.
- **ROLE_ADMIN**: Full access to all operations.
- **ROLE_TECHNICIAN**: Access to assigned Work_Orders and vehicle data.
- **ROLE_CUSTOMER**: Read-only access to own vehicles and Work_Orders.
- **Bearer_Token**: An HTTP Authorization header value of the form `Authorization: Bearer <JWT>`.

---

## Requirements

### Requirement 1: JWT Validation

**User Story:** As a system integrator, I want the Maintenance Service to validate JWTs issued by the Auth Service so that only authenticated users can access business endpoints.

#### Acceptance Criteria

1. WHEN the service receives a request with a valid Bearer_Token, it SHALL extract the username and roles from the JWT and make them available to the request context.
2. WHEN the service receives a request without an Authorization header or with a missing token, it SHALL return HTTP 401.
3. WHEN the service receives a request with an expired JWT, it SHALL return HTTP 401.
4. WHEN the service receives a request with a malformed or tampered JWT, it SHALL return HTTP 401.
5. THE service SHALL validate JWTs using the JWT_Secret and HS256 algorithm, configured via an application property read from an environment variable.
6. THE service SHALL operate as a stateless JWT resource server and SHALL NOT perform password-based authentication or issue tokens.
7. THE service SHALL require a valid Bearer_Token for all routes under `/api/v1/**` and SHALL permit health/actuator endpoints without authentication.

---

### Requirement 2: Vehicle Management

**User Story:** As a user, I want to register and manage vehicles by VIN so that the shop can track maintenance history per vehicle.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/vehicles` with a valid VIN and vehicle details, the service SHALL persist the vehicle and return HTTP 201 with the created resource.
2. WHEN a POST request is made to `/api/v1/vehicles` with a VIN that already exists, the service SHALL return HTTP 409.
3. WHEN a GET request is made to `/api/v1/vehicles/{id}`, the service SHALL return HTTP 200 with the vehicle resource, or HTTP 404 if not found.
4. WHEN a GET request is made to `/api/v1/vehicles`, the service SHALL return a paginated list of vehicles.
5. WHEN a PUT request is made to `/api/v1/vehicles/{id}` with valid updated fields, the service SHALL update the vehicle and return HTTP 200.
6. WHEN a DELETE request is made to `/api/v1/vehicles/{id}`, the service SHALL soft-delete the record and return HTTP 204.
7. THE service SHALL validate that a VIN is exactly 17 alphanumeric characters and SHALL return HTTP 400 with a descriptive error if validation fails.
8. WHERE the authenticated user has ROLE_CUSTOMER, the service SHALL restrict vehicle read operations to vehicles associated with that user's account.

---

### Requirement 3: Work Order Management

**User Story:** As a service advisor or technician, I want to create and manage work orders so that maintenance jobs are tracked from intake to completion.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/work-orders` with a valid vehicle id, description, and assigned technician, the service SHALL create a Work_Order with status `OPEN` and return HTTP 201.
2. WHEN a GET request is made to `/api/v1/work-orders/{id}`, the service SHALL return HTTP 200 with the full Work_Order including Part_Lines and Labor_Lines, or HTTP 404 if not found.
3. WHEN a GET request is made to `/api/v1/work-orders`, the service SHALL return a paginated, filterable list supporting filters by status, vehicle id, and technician id.
4. WHEN a PATCH request is made to `/api/v1/work-orders/{id}/status` with a valid target status, the service SHALL transition the status and return HTTP 200.
5. IF a PATCH request is made with an invalid status transition, the service SHALL return HTTP 422 with a descriptive error.
6. THE service SHALL enforce the state machine: `OPEN` → `IN_PROGRESS` → `PENDING_PARTS` → `IN_PROGRESS` → `COMPLETED` → `INVOICED`. Transitions that skip states SHALL be rejected.
7. WHEN a Work_Order status changes, the service SHALL record a status history entry with previous status, new status, timestamp, and the username of the actor.
8. WHERE the authenticated user has ROLE_TECHNICIAN, the service SHALL restrict Work_Order write operations to Work_Orders assigned to that technician.

---

### Requirement 4: Parts and Labor Line Items

**User Story:** As a technician or service advisor, I want to add parts and labor to a work order so that job cost is tracked accurately.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/work-orders/{id}/parts` with a valid part name, quantity (positive integer), and unit cost (positive decimal), the service SHALL add a Part_Line and return HTTP 201.
2. WHEN a POST request is made to `/api/v1/work-orders/{id}/labor` with a valid description, hours (positive decimal), and rate (positive decimal), the service SHALL add a Labor_Line and return HTTP 201.
3. WHEN a DELETE request is made to `/api/v1/work-orders/{id}/parts/{partLineId}`, the service SHALL remove the Part_Line and return HTTP 204.
4. WHEN a DELETE request is made to `/api/v1/work-orders/{id}/labor/{laborLineId}`, the service SHALL remove the Labor_Line and return HTTP 204.
5. IF a line item modification is attempted on a Work_Order with status `COMPLETED` or `INVOICED`, the service SHALL return HTTP 422 with the message "Cannot modify line items on a closed work order".
6. THE service SHALL calculate and return the total cost as the sum of (quantity × unit_cost) for all Part_Lines plus the sum of (hours × rate) for all Labor_Lines.

---

### Requirement 5: Service Scheduling

**User Story:** As a service advisor, I want to schedule future maintenance for a vehicle so that customers are reminded and bays can be planned.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/schedules` with a valid vehicle id, scheduled date-time, service type, and optional bay id, the service SHALL create a Service_Schedule and return HTTP 201.
2. WHEN a GET request is made to `/api/v1/schedules` with an optional date range filter, the service SHALL return a paginated list within that range.
3. WHEN a GET request is made to `/api/v1/schedules?vehicleId={id}`, the service SHALL return all schedules for that vehicle.
4. IF a POST request is made with a bay id and the bay already has a confirmed schedule overlapping the requested time slot (within 2 hours), the service SHALL return HTTP 409 with the message "Bay is not available for the requested time slot".
5. WHEN a DELETE request is made to `/api/v1/schedules/{id}`, the service SHALL cancel the schedule and return HTTP 204.
6. THE service SHALL store all date-times in UTC and SHALL accept and return date-times in ISO 8601 format.

---

### Requirement 6: Technician and Bay Management

**User Story:** As an admin, I want to manage technicians and bays so that work orders can be properly assigned and shop capacity is visible.

#### Acceptance Criteria

1. WHEN a GET request is made to `/api/v1/technicians`, the service SHALL return a list of all active technicians with their id, name, and current Work_Order count.
2. WHEN a GET request is made to `/api/v1/bays`, the service SHALL return a list of all bays with their id, name, and current availability status.
3. WHEN a PUT request is made to `/api/v1/work-orders/{id}/assign` with a valid technician id and bay id, the service SHALL assign them to the Work_Order and return HTTP 200.
4. IF a PUT request is made with a technician id that does not exist, the service SHALL return HTTP 404 with a descriptive error.
5. WHERE the authenticated user has ROLE_ADMIN, the service SHALL permit creation, update, and deactivation of Technician and Bay records via `/api/v1/technicians` and `/api/v1/bays`.

---

### Requirement 7: API Documentation

**User Story:** As a developer integrating with the Maintenance Service, I want an OpenAPI specification so that I can understand and consume the API without reading source code.

#### Acceptance Criteria

1. THE service SHALL expose an OpenAPI 3.x specification at `/v3/api-docs`.
2. THE service SHALL expose a Swagger UI at `/swagger-ui.html`.
3. THE service SHALL document all request/response schemas, required fields, and HTTP status codes for every endpoint.
4. THE service SHALL document the Bearer token security scheme in the OpenAPI spec so Swagger UI allows authentication before test calls.

---

### Requirement 8: Database Bootstrap

**User Story:** As a developer, I want idempotent database bootstrap scripts so the Maintenance Service schema and seed data are created automatically on first start.

#### Acceptance Criteria

1. THE system SHALL provide `db/vehicle-maintenance-service/init.sql` that creates the `vehicles`, `work_orders`, `work_order_status_history`, `part_lines`, `labor_lines`, `service_schedules`, `technicians`, and `bays` tables using `CREATE TABLE IF NOT EXISTS`.
2. THE script SHALL insert seed rows for at least two default bays (`Bay 1`, `Bay 2`) and one default admin technician using `INSERT IGNORE`.
3. THE script SHALL be mounted into the MySQL container's `/docker-entrypoint-initdb.d/` directory via Docker Compose.
4. IF the MySQL container is started with an already-initialised volume, THE script SHALL produce no errors and SHALL NOT overwrite existing data.

---

### Requirement 9: Build and Containerisation

**User Story:** As a developer, I want the Maintenance Service to be buildable and runnable in Docker so it can be started as part of the full stack.

#### Acceptance Criteria

1. THE service directory SHALL contain a Maven wrapper (`mvnw` / `mvnw.cmd`) so it can be built with `./mvnw clean package -DskipTests`.
2. THE service SHALL have a `Dockerfile` that builds the JAR and produces a runnable image.
3. THE service container SHALL accept database credentials and the JWT_Secret via environment variables.
4. THE service container SHALL expose port 8081.
5. THE service container SHALL depend on the `mysql` service being healthy before starting.
