# Requirements Document

## Introduction

This document defines requirements for a multi-service vehicle maintenance system composed of three components:

1. **Auth Service** — an existing Spring Boot 3.x application (this repo) that handles user registration, login, JWT issuance, and role management backed by MySQL.
2. **Maintenance Domain Service** — a new Spring Boot 3.x backend that owns all vehicle/maintenance business logic: vehicles (VIN), work orders, service schedules, parts/labor lines, technicians, bays, and status history. It validates JWTs issued by the Auth Service but performs no password-based authentication itself.
3. **UI** — a new frontend (framework TBD: React, Vue, or Angular) that provides screens for login/register, dashboards, work orders, and scheduling. It calls the Auth Service for authentication and the Maintenance Domain Service for all business operations, passing the JWT as a Bearer token.

The system uses REST APIs and MySQL as locked technology choices. Each service owns its own database schema — no shared tables between services.

---

## Glossary

- **Auth_Service**: The existing Spring Boot application in this repository responsible for identity and access management.
- **Domain_Service**: The new Spring Boot microservice responsible for all vehicle maintenance business logic.
- **UI**: The new frontend application (React, Vue, or Angular) used by end users.
- **JWT**: JSON Web Token — a signed, stateless bearer token issued by the Auth_Service and validated by the Domain_Service.
- **JWT_Secret**: A shared symmetric key (HS256) configured identically in both the Auth_Service and the Domain_Service to enable token validation without inter-service calls.
- **VIN**: Vehicle Identification Number — a 17-character alphanumeric identifier unique to each vehicle.
- **Work_Order**: A record representing a maintenance job for a specific vehicle, including status, assigned technician, bay, parts, and labor lines.
- **Service_Schedule**: A planned future maintenance event associated with a vehicle and optionally a Work_Order.
- **Technician**: A user with the ROLE_TECHNICIAN role who can be assigned to Work_Orders.
- **Bay**: A physical service bay in the shop that can be assigned to a Work_Order.
- **Part_Line**: A line item on a Work_Order representing a part used, with quantity and unit cost.
- **Labor_Line**: A line item on a Work_Order representing labor performed, with hours and rate.
- **ROLE_ADMIN**: A role granting full access to all system operations.
- **ROLE_TECHNICIAN**: A role granting access to assigned Work_Orders and vehicle data.
- **ROLE_CUSTOMER**: A role granting read-only access to a customer's own vehicles and Work_Orders.
- **OpenAPI**: The OpenAPI 3.x specification used to document the Domain_Service REST API.
- **Bearer_Token**: An HTTP Authorization header value of the form `Authorization: Bearer <JWT>`.

---

## Requirements

### Requirement 1: User Registration and Authentication

**User Story:** As a user, I want to register and log in so that I can access the system with an appropriate role.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/auth/signup` with a valid username, email, password, and optional role list, THE Auth_Service SHALL create a new user account and return a success message.
2. WHEN a POST request is made to `/api/auth/signup` with a username that already exists, THE Auth_Service SHALL return HTTP 400 with the message "Error: Username is already taken!".
3. WHEN a POST request is made to `/api/auth/signup` with an email that already exists, THE Auth_Service SHALL return HTTP 400 with the message "Error: Email is already in use!".
4. WHEN a POST request is made to `/api/auth/signin` with valid credentials, THE Auth_Service SHALL return HTTP 200 with a JWT, the user's id, username, email, and role list.
5. WHEN a POST request is made to `/api/auth/signin` with invalid credentials, THE Auth_Service SHALL return HTTP 401.
6. THE Auth_Service SHALL sign all issued JWTs using HS256 with the configured JWT_Secret and set an expiration of `bezkoder.app.jwtExpirationMs` milliseconds from issuance.
7. THE Auth_Service SHALL store user and role data exclusively in its own MySQL schema and SHALL NOT share tables with the Domain_Service.

---

### Requirement 2: JWT Validation in the Domain Service

**User Story:** As a system integrator, I want the Domain Service to validate JWTs issued by the Auth Service so that only authenticated users can access business endpoints.

#### Acceptance Criteria

1. WHEN the Domain_Service receives a request with a valid Bearer_Token in the Authorization header, THE Domain_Service SHALL extract the username and roles from the JWT and make them available to the request context.
2. WHEN the Domain_Service receives a request without an Authorization header or with a missing token, THE Domain_Service SHALL return HTTP 401.
3. WHEN the Domain_Service receives a request with an expired JWT, THE Domain_Service SHALL return HTTP 401.
4. WHEN the Domain_Service receives a request with a malformed or tampered JWT, THE Domain_Service SHALL return HTTP 401.
5. THE Domain_Service SHALL validate JWTs using the same JWT_Secret and HS256 algorithm as the Auth_Service, configured via an application property (e.g., `app.jwtSecret`).
6. THE Domain_Service SHALL operate as a stateless JWT resource server and SHALL NOT perform password-based authentication or issue tokens.
7. THE Domain_Service SHALL expose all business endpoints under `/api/v1/**` and SHALL require a valid Bearer_Token for all routes except health/actuator endpoints.

---

### Requirement 3: Vehicle Management

**User Story:** As a user, I want to register and manage vehicles by VIN so that the shop can track maintenance history per vehicle.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/vehicles` with a valid VIN and vehicle details, THE Domain_Service SHALL persist the vehicle and return HTTP 201 with the created vehicle resource.
2. WHEN a POST request is made to `/api/v1/vehicles` with a VIN that already exists, THE Domain_Service SHALL return HTTP 409.
3. WHEN a GET request is made to `/api/v1/vehicles/{id}`, THE Domain_Service SHALL return HTTP 200 with the vehicle resource if it exists, or HTTP 404 if it does not.
4. WHEN a GET request is made to `/api/v1/vehicles`, THE Domain_Service SHALL return a paginated list of vehicles.
5. WHEN a PUT request is made to `/api/v1/vehicles/{id}` with valid updated fields, THE Domain_Service SHALL update the vehicle and return HTTP 200 with the updated resource.
6. WHEN a DELETE request is made to `/api/v1/vehicles/{id}`, THE Domain_Service SHALL soft-delete the vehicle record and return HTTP 204.
7. THE Domain_Service SHALL validate that a VIN is exactly 17 alphanumeric characters and SHALL return HTTP 400 with a descriptive error if validation fails.
8. WHERE the authenticated user has ROLE_CUSTOMER, THE Domain_Service SHALL restrict vehicle read operations to vehicles associated with that user's account.

---

### Requirement 4: Work Order Management

**User Story:** As a service advisor or technician, I want to create and manage work orders so that maintenance jobs are tracked from intake to completion.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/work-orders` with a valid vehicle id, description, and assigned technician, THE Domain_Service SHALL create a Work_Order with status `OPEN` and return HTTP 201.
2. WHEN a GET request is made to `/api/v1/work-orders/{id}`, THE Domain_Service SHALL return HTTP 200 with the full Work_Order resource including Part_Lines and Labor_Lines, or HTTP 404 if not found.
3. WHEN a GET request is made to `/api/v1/work-orders`, THE Domain_Service SHALL return a paginated, filterable list of Work_Orders supporting filters by status, vehicle id, and technician id.
4. WHEN a PATCH request is made to `/api/v1/work-orders/{id}/status` with a valid target status, THE Domain_Service SHALL transition the Work_Order status according to the allowed state machine and return HTTP 200.
5. IF a PATCH request is made to `/api/v1/work-orders/{id}/status` with an invalid status transition, THEN THE Domain_Service SHALL return HTTP 422 with a descriptive error message.
6. THE Domain_Service SHALL enforce the Work_Order status state machine: `OPEN` → `IN_PROGRESS` → `PENDING_PARTS` → `IN_PROGRESS` → `COMPLETED` → `INVOICED`. Direct transitions that skip states SHALL be rejected.
7. WHEN a Work_Order status changes, THE Domain_Service SHALL record a status history entry with the previous status, new status, timestamp, and the username of the user who made the change.
8. WHERE the authenticated user has ROLE_TECHNICIAN, THE Domain_Service SHALL restrict Work_Order write operations to Work_Orders assigned to that technician.

---

### Requirement 5: Parts and Labor Line Items

**User Story:** As a technician or service advisor, I want to add parts and labor to a work order so that the job cost can be tracked accurately.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/work-orders/{id}/parts` with a valid part name, quantity (positive integer), and unit cost (positive decimal), THE Domain_Service SHALL add a Part_Line to the Work_Order and return HTTP 201.
2. WHEN a POST request is made to `/api/v1/work-orders/{id}/labor` with a valid description, hours (positive decimal), and rate (positive decimal), THE Domain_Service SHALL add a Labor_Line to the Work_Order and return HTTP 201.
3. WHEN a DELETE request is made to `/api/v1/work-orders/{id}/parts/{partLineId}`, THE Domain_Service SHALL remove the Part_Line and return HTTP 204.
4. WHEN a DELETE request is made to `/api/v1/work-orders/{id}/labor/{laborLineId}`, THE Domain_Service SHALL remove the Labor_Line and return HTTP 204.
5. IF a Part_Line or Labor_Line modification is attempted on a Work_Order with status `COMPLETED` or `INVOICED`, THEN THE Domain_Service SHALL return HTTP 422 with the message "Cannot modify line items on a closed work order".
6. THE Domain_Service SHALL calculate and return the total cost of a Work_Order as the sum of (quantity × unit_cost) for all Part_Lines plus the sum of (hours × rate) for all Labor_Lines.

---

### Requirement 6: Service Scheduling

**User Story:** As a service advisor, I want to schedule future maintenance for a vehicle so that customers are reminded and bays can be planned.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/v1/schedules` with a valid vehicle id, scheduled date-time, service type, and optional bay id, THE Domain_Service SHALL create a Service_Schedule and return HTTP 201.
2. WHEN a GET request is made to `/api/v1/schedules` with an optional date range filter, THE Domain_Service SHALL return a paginated list of Service_Schedules within that range.
3. WHEN a GET request is made to `/api/v1/schedules?vehicleId={id}`, THE Domain_Service SHALL return all Service_Schedules for the specified vehicle.
4. IF a POST request is made to `/api/v1/schedules` with a bay id and the bay already has a confirmed Service_Schedule overlapping the requested time slot (within 2 hours), THEN THE Domain_Service SHALL return HTTP 409 with the message "Bay is not available for the requested time slot".
5. WHEN a DELETE request is made to `/api/v1/schedules/{id}`, THE Domain_Service SHALL cancel the Service_Schedule and return HTTP 204.
6. THE Domain_Service SHALL store all scheduled date-times in UTC and SHALL accept and return date-times in ISO 8601 format.

---

### Requirement 7: Technician and Bay Management

**User Story:** As an admin, I want to manage technicians and bays so that work orders can be properly assigned and shop capacity is visible.

#### Acceptance Criteria

1. WHEN a GET request is made to `/api/v1/technicians`, THE Domain_Service SHALL return a list of all active technicians with their id, name, and current Work_Order count.
2. WHEN a GET request is made to `/api/v1/bays`, THE Domain_Service SHALL return a list of all bays with their id, name, and current availability status.
3. WHEN a PUT request is made to `/api/v1/work-orders/{id}/assign` with a valid technician id and bay id, THE Domain_Service SHALL assign the technician and bay to the Work_Order and return HTTP 200.
4. IF a PUT request is made to `/api/v1/work-orders/{id}/assign` with a technician id that does not exist, THEN THE Domain_Service SHALL return HTTP 404 with a descriptive error.
5. WHERE the authenticated user has ROLE_ADMIN, THE Domain_Service SHALL permit creation, update, and deactivation of Technician and Bay records via `/api/v1/technicians` and `/api/v1/bays` endpoints.

---

### Requirement 8: API Documentation

**User Story:** As a developer integrating with the Domain Service, I want an OpenAPI specification so that I can understand and consume the API without reading source code.

#### Acceptance Criteria

1. THE Domain_Service SHALL expose an OpenAPI 3.x specification at `/v3/api-docs`.
2. THE Domain_Service SHALL expose a Swagger UI at `/swagger-ui.html` for interactive API exploration.
3. THE Domain_Service SHALL document all request and response schemas, required fields, and HTTP status codes for every endpoint in the OpenAPI specification.
4. THE Domain_Service SHALL document the Bearer token security scheme in the OpenAPI specification so that the Swagger UI allows users to authenticate before making test calls.

---

### Requirement 9: UI — Authentication Screens

**User Story:** As an end user, I want login and registration screens so that I can access the system from a browser.

#### Acceptance Criteria

1. THE UI SHALL provide a login screen that collects username and password and submits them to `POST /api/auth/signin`.
2. WHEN the Auth_Service returns a successful sign-in response, THE UI SHALL store the JWT in browser storage and redirect the user to the main dashboard.
3. WHEN the Auth_Service returns HTTP 401 on sign-in, THE UI SHALL display an error message to the user without exposing the raw HTTP status code.
4. THE UI SHALL provide a registration screen that collects username, email, and password and submits them to `POST /api/auth/signup`.
5. WHEN the user logs out, THE UI SHALL remove the JWT from browser storage and redirect to the login screen.
6. WHILE the JWT is present in browser storage, THE UI SHALL attach it as a Bearer_Token on every request to the Domain_Service.

---

### Requirement 10: UI — Dashboard and Work Order Screens

**User Story:** As a service advisor or technician, I want dashboard and work order screens so that I can manage the shop's daily operations from the browser.

#### Acceptance Criteria

1. THE UI SHALL provide a dashboard screen that displays a summary of open Work_Orders, today's Service_Schedules, and bay availability.
2. THE UI SHALL provide a work order list screen that supports filtering by status, vehicle, and technician.
3. THE UI SHALL provide a work order detail screen that displays all Work_Order fields, Part_Lines, Labor_Lines, total cost, and status history.
4. WHEN the user submits a status transition on the work order detail screen, THE UI SHALL call `PATCH /api/v1/work-orders/{id}/status` and refresh the displayed status without a full page reload.
5. IF the Domain_Service returns HTTP 401 on any request, THEN THE UI SHALL redirect the user to the login screen and clear the stored JWT.
6. THE UI SHALL provide a scheduling screen that allows creating and viewing Service_Schedules with a calendar or list view.

---

### Requirement 11: Cross-Cutting — Project Structure and Shared Context

**User Story:** As a developer, I want a clear multi-project structure so that shared concerns like JWT configuration are managed consistently without coupling the services.

#### Acceptance Criteria

1. THE Auth_Service and THE Domain_Service SHALL each be independent Maven projects with their own `pom.xml` and SHALL NOT share a compiled JAR or module dependency on each other.
2. THE Auth_Service and THE Domain_Service SHALL share the JWT_Secret value only through environment-specific configuration (e.g., environment variables or a secrets manager) and SHALL NOT hard-code the secret in source control.
3. THE Domain_Service SHALL duplicate only the JWT validation logic (filter + utility class) from the Auth_Service, as this is the accepted pattern for stateless microservice JWT resource servers.
4. THE UI project SHALL be a standalone frontend project in its own directory, separate from both Spring Boot projects.
5. THE system SHALL be organized as a monorepo with the following top-level structure:
   - `auth-service/` — the existing Spring Boot auth application
   - `maintenance-service/` — the new Domain_Service Spring Boot application
   - `ui/` — the new frontend application
6. WHERE a Docker Compose file is provided, THE system SHALL define services for `auth-service`, `maintenance-service`, `ui`, and `mysql` so that the full stack can be started with a single command for local development.

---

### Requirement 12: Build Scripts, Docker Compose, and Database Bootstrap

**User Story:** As a developer, I want build scripts, a Docker Compose file, and idempotent database bootstrap scripts so that the full system can be built, deployed, and initialised from a clean state with a single set of commands.

#### Acceptance Criteria

1. THE Auth_Service directory SHALL contain a Maven wrapper script (`mvnw` / `mvnw.cmd`) so that THE Auth_Service can be built with `./mvnw clean package -DskipTests` without requiring a globally installed Maven binary.
2. THE Domain_Service directory SHALL contain a Maven wrapper script (`mvnw` / `mvnw.cmd`) so that THE Domain_Service can be built with `./mvnw clean package -DskipTests` without requiring a globally installed Maven binary.
3. THE UI directory SHALL contain a `package.json` with a `build` script so that THE UI can be built with `npm run build` (or `yarn build`) without additional configuration.
4. THE system SHALL provide a `docker-compose.yml` at the monorepo root that defines exactly four services: `mysql`, `auth-service`, `maintenance-service`, and `ui`.
5. WHEN `docker compose up` is executed at the monorepo root, THE Docker_Compose SHALL start all four services in dependency order: `mysql` first, then `auth-service` and `maintenance-service` in parallel, then `ui`.
6. THE Docker_Compose SHALL inject the JWT_Secret into both `auth-service` and `maintenance-service` containers via an environment variable (e.g., `JWT_SECRET`) so that the secret is never hard-coded in any image or source file.
7. THE Docker_Compose SHALL inject database credentials (host, port, database name, username, password) into `auth-service` and `maintenance-service` containers via environment variables.
8. THE Docker_Compose SHALL define a health check for the `mysql` service that polls `mysqladmin ping` at 10-second intervals with a 5-second timeout and 5 retries, and SHALL configure `auth-service` and `maintenance-service` to depend on `mysql` with condition `service_healthy`.
9. THE Docker_Compose SHALL expose `auth-service` on host port 8080, `maintenance-service` on host port 8081, and `ui` on host port 3000.
10. THE system SHALL provide a SQL bootstrap script `db/auth-service/init.sql` that creates the `users`, `roles`, and `user_roles` tables for the Auth_Service schema using `CREATE TABLE IF NOT EXISTS` statements so that the script is safe to re-run.
11. THE `db/auth-service/init.sql` script SHALL insert seed rows for `ROLE_ADMIN`, `ROLE_TECHNICIAN`, and `ROLE_CUSTOMER` into the `roles` table using `INSERT IGNORE` (or equivalent idempotent syntax) so that roles are always present after initialisation.
12. THE system SHALL provide a SQL bootstrap script `db/maintenance-service/init.sql` that creates the `vehicles`, `work_orders`, `work_order_status_history`, `part_lines`, `labor_lines`, `service_schedules`, `technicians`, and `bays` tables for the Domain_Service schema using `CREATE TABLE IF NOT EXISTS` statements so that the script is safe to re-run.
13. THE `db/maintenance-service/init.sql` script SHALL insert seed rows for at least two default bays (e.g., `Bay 1`, `Bay 2`) and one default admin technician record using `INSERT IGNORE` (or equivalent idempotent syntax).
14. THE Docker_Compose SHALL mount `db/auth-service/init.sql` and `db/maintenance-service/init.sql` into the `mysql` container's `/docker-entrypoint-initdb.d/` directory so that MySQL executes them automatically on first start.
15. IF the `mysql` container is started with an already-initialised data volume, THEN THE bootstrap scripts SHALL produce no errors and SHALL NOT overwrite existing data, because all DDL uses `IF NOT EXISTS` and all DML uses `INSERT IGNORE` or equivalent.
