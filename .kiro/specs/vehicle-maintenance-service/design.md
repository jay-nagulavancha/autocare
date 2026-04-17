# Design Document — Maintenance Service

## Overview

The Maintenance Service is a standalone Spring Boot 3.x REST API that owns all vehicle maintenance business logic for the Vehicle Maintenance System. It runs on port 8081, exposes all business endpoints under `/api/v1/`, and validates JWTs issued by the Auth Service using a shared HS256 secret — no inter-service calls at runtime.

The service is a pure JWT resource server: it never issues tokens, never handles passwords, and never calls back to the Auth Service. Every request is independently authenticated by verifying the JWT signature against `JWT_SECRET`.

### Key Design Decisions

- **JWT validation is duplicated by design.** `AuthTokenFilter` and `JwtUtils` are copied from the user-auth-service pattern. This is the accepted stateless microservice approach.
- **Roles are extracted from JWT claims**, not from a local database. The maintenance service has no `users` or `roles` tables.
- **Soft deletes** on vehicles (`deleted` flag) — no hard deletes.
- **Work order state machine** is enforced in the service layer via an explicit allowed-transitions map, not via database constraints.
- **Bay conflict detection** uses a 2-hour exclusion window checked in the service layer before persisting a schedule.
- **All datetimes** are stored and returned in UTC, serialized as ISO 8601.
- **Cost calculation** is computed on-the-fly from part/labor lines — not stored as a denormalized column.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    vehicle-maintenance-service (:8081)               │
│                                                             │
│  HTTP Request                                               │
│      │                                                      │
│      ▼                                                      │
│  AuthTokenFilter  ──► JwtUtils.validateJwtToken()           │
│      │                    (HS256, JWT_SECRET env var)       │
│      │                                                      │
│      ▼                                                      │
│  SecurityContextHolder  (username + roles from JWT claims)  │
│      │                                                      │
│      ▼                                                      │
│  @RestController  ──► @Service  ──► @Repository  ──► MySQL  │
│  (Spring Web)         (business      (Spring Data           │
│                        logic)         JPA)                  │
└─────────────────────────────────────────────────────────────┘
```

The filter chain is stateless (no HTTP session). Role-based access is enforced via `@PreAuthorize` annotations on service or controller methods, enabled by `@EnableMethodSecurity`.

### Dependency Flow

```
Controller → Service → Repository → JPA Entity → MySQL (maintenance_db)
Controller → Service → WorkOrderStateMachine (pure logic, no DB)
Controller → Service → BayConflictChecker (pure logic, queries DB)
Controller → Service → CostCalculator (pure logic, no DB)
```

---

## Components and Interfaces

### Package Structure

```
com.bezkoder.maintenance
├── MaintenanceServiceApplication.java
├── config/
│   └── OpenApiConfig.java
├── security/
│   ├── WebSecurityConfig.java
│   ├── jwt/
│   │   ├── AuthTokenFilter.java        ← copied/adapted from user-auth-service
│   │   ├── AuthEntryPointJwt.java      ← copied from user-auth-service
│   │   └── JwtUtils.java               ← adapted (no token generation)
│   └── services/
│       └── JwtUserDetails.java         ← lightweight, no DB lookup
├── model/
│   ├── Vehicle.java
│   ├── WorkOrder.java
│   ├── WorkOrderStatus.java            ← enum
│   ├── WorkOrderStatusHistory.java
│   ├── PartLine.java
│   ├── LaborLine.java
│   ├── ServiceSchedule.java
│   ├── Technician.java
│   └── Bay.java
├── repository/
│   ├── VehicleRepository.java
│   ├── WorkOrderRepository.java
│   ├── WorkOrderStatusHistoryRepository.java
│   ├── PartLineRepository.java
│   ├── LaborLineRepository.java
│   ├── ServiceScheduleRepository.java
│   ├── TechnicianRepository.java
│   └── BayRepository.java
├── service/
│   ├── VehicleService.java
│   ├── WorkOrderService.java
│   ├── PartLaborService.java
│   ├── ScheduleService.java
│   ├── TechnicianService.java
│   ├── BayService.java
│   ├── WorkOrderStateMachine.java      ← pure logic
│   ├── BayConflictChecker.java         ← pure logic
│   └── CostCalculator.java             ← pure logic
├── controller/
│   ├── VehicleController.java
│   ├── WorkOrderController.java
│   ├── ScheduleController.java
│   ├── TechnicianController.java
│   └── BayController.java
├── payload/
│   ├── request/
│   │   ├── CreateVehicleRequest.java
│   │   ├── UpdateVehicleRequest.java
│   │   ├── CreateWorkOrderRequest.java
│   │   ├── StatusTransitionRequest.java
│   │   ├── AssignRequest.java
│   │   ├── AddPartLineRequest.java
│   │   ├── AddLaborLineRequest.java
│   │   ├── CreateScheduleRequest.java
│   │   ├── CreateTechnicianRequest.java
│   │   └── CreateBayRequest.java
│   └── response/
│       ├── VehicleResponse.java
│       ├── WorkOrderResponse.java
│       ├── PartLineResponse.java
│       ├── LaborLineResponse.java
│       ├── ScheduleResponse.java
│       ├── TechnicianResponse.java
│       ├── BayResponse.java
│       └── ErrorResponse.java
└── exception/
    ├── ResourceNotFoundException.java
    ├── DuplicateVinException.java
    ├── InvalidStatusTransitionException.java
    ├── ClosedWorkOrderException.java
    ├── BayConflictException.java
    └── GlobalExceptionHandler.java
```

### JWT Filter Chain (adapted from user-auth-service)

The maintenance service has **no local user database**. The `AuthTokenFilter` extracts username and roles directly from JWT claims without a `UserDetailsService` DB lookup:

```
AuthTokenFilter.doFilterInternal()
  ├── parseJwt(request)  → extract Bearer token from Authorization header
  ├── JwtUtils.validateJwtToken(jwt)  → verify HS256 signature + expiry
  ├── JwtUtils.getUserNameFromJwtToken(jwt)  → extract subject claim
  ├── JwtUtils.getRolesFromJwtToken(jwt)  → extract roles claim as List<GrantedAuthority>
  └── SecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken)
```

Key difference from user-auth-service: `JwtUtils` in the maintenance service adds `getRolesFromJwtToken()` to read the `roles` claim embedded by the user-auth-service at token issuance. No `UserDetailsService` or database lookup is needed.

`WebSecurityConfig` permits:
- `/actuator/health` — no auth
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` — no auth
- All `/api/v1/**` — requires authentication

Role-based method security uses `@PreAuthorize("hasRole('ADMIN')")` etc., enabled by `@EnableMethodSecurity`.

### WorkOrderStateMachine

A pure-logic component (no Spring beans, no DB) that encodes the allowed transitions:

```java
// Allowed transitions map
OPEN          → [IN_PROGRESS]
IN_PROGRESS   → [PENDING_PARTS, COMPLETED]
PENDING_PARTS → [IN_PROGRESS]
COMPLETED     → [INVOICED]
INVOICED      → []  (terminal)
```

```java
public class WorkOrderStateMachine {
    public boolean isValidTransition(WorkOrderStatus from, WorkOrderStatus to);
    public Set<WorkOrderStatus> allowedNext(WorkOrderStatus from);
}
```

Note: `IN_PROGRESS → COMPLETED` is a valid direct transition (parts may not always be needed). The full path `OPEN → IN_PROGRESS → PENDING_PARTS → IN_PROGRESS → COMPLETED → INVOICED` is the typical flow but `PENDING_PARTS` is optional.

### BayConflictChecker

Pure-logic component that determines whether a proposed schedule time conflicts with an existing confirmed schedule for the same bay:

```java
public class BayConflictChecker {
    // Returns true if |proposed - existing| < 2 hours for any confirmed schedule on the same bay
    public boolean hasConflict(Long bayId, LocalDateTime proposed, Long excludeScheduleId,
                               ServiceScheduleRepository repo);
}
```

The 2-hour window is symmetric: a new booking at T conflicts with any existing booking in the range `[T-2h, T+2h)`.

### CostCalculator

Pure-logic component with no dependencies:

```java
public class CostCalculator {
    public BigDecimal calculate(List<PartLine> parts, List<LaborLine> labors);
    // = sum(part.quantity * part.unitCost) + sum(labor.hours * labor.rate)
}
```

---

## Data Models

### Entities

#### Vehicle
```java
@Entity @Table(name = "vehicles")
public class Vehicle {
    @Id @GeneratedValue Long id;
    @Column(unique = true, length = 17) String vin;   // validated: [A-Za-z0-9]{17}
    String make;
    String model;
    Integer year;
    String ownerUsername;   // matches JWT subject for ROLE_CUSTOMER filtering
    boolean deleted = false;
    LocalDateTime createdAt;
}
```

#### WorkOrder
```java
@Entity @Table(name = "work_orders")
public class WorkOrder {
    @Id @GeneratedValue Long id;
    @ManyToOne Vehicle vehicle;
    @ManyToOne Technician technician;   // nullable until assigned
    @ManyToOne Bay bay;                 // nullable until assigned
    @Enumerated(EnumType.STRING) WorkOrderStatus status;  // default OPEN
    String description;
    LocalDateTime createdAt;
    @OneToMany(mappedBy = "workOrder", cascade = ALL) List<PartLine> partLines;
    @OneToMany(mappedBy = "workOrder", cascade = ALL) List<LaborLine> laborLines;
    @OneToMany(mappedBy = "workOrder", cascade = ALL) List<WorkOrderStatusHistory> statusHistory;
}
```

#### WorkOrderStatus (enum)
```java
public enum WorkOrderStatus {
    OPEN, IN_PROGRESS, PENDING_PARTS, COMPLETED, INVOICED
}
```

#### WorkOrderStatusHistory
```java
@Entity @Table(name = "work_order_status_history")
public class WorkOrderStatusHistory {
    @Id @GeneratedValue Long id;
    @ManyToOne WorkOrder workOrder;
    @Enumerated(EnumType.STRING) WorkOrderStatus previousStatus;
    @Enumerated(EnumType.STRING) WorkOrderStatus newStatus;
    String changedBy;       // JWT subject (username)
    LocalDateTime changedAt;
}
```

#### PartLine
```java
@Entity @Table(name = "part_lines")
public class PartLine {
    @Id @GeneratedValue Long id;
    @ManyToOne WorkOrder workOrder;
    String partName;
    @Min(1) Integer quantity;
    @DecimalMin("0.01") BigDecimal unitCost;
}
```

#### LaborLine
```java
@Entity @Table(name = "labor_lines")
public class LaborLine {
    @Id @GeneratedValue Long id;
    @ManyToOne WorkOrder workOrder;
    String description;
    @DecimalMin("0.01") BigDecimal hours;
    @DecimalMin("0.01") BigDecimal rate;
}
```

#### ServiceSchedule
```java
@Entity @Table(name = "service_schedules")
public class ServiceSchedule {
    @Id @GeneratedValue Long id;
    @ManyToOne Vehicle vehicle;
    @ManyToOne(optional = true) Bay bay;
    LocalDateTime scheduledAt;   // stored as UTC
    String serviceType;
    String status;               // CONFIRMED, CANCELLED
}
```

#### Technician
```java
@Entity @Table(name = "technicians")
public class Technician {
    @Id @GeneratedValue Long id;
    String name;
    boolean active = true;
}
```

#### Bay
```java
@Entity @Table(name = "bays")
public class Bay {
    @Id @GeneratedValue Long id;
    String name;
    boolean active = true;
}
```

### Repository Interfaces

| Repository | Key custom methods |
|---|---|
| `VehicleRepository` | `findByVin`, `findByOwnerUsernameAndDeletedFalse`, `findAllByDeletedFalse(Pageable)` |
| `WorkOrderRepository` | `findByStatusAndVehicleIdAndTechnicianId` (all optional filters, Pageable) |
| `WorkOrderStatusHistoryRepository` | `findByWorkOrderIdOrderByChangedAtDesc` |
| `PartLineRepository` | `findByWorkOrderId` |
| `LaborLineRepository` | `findByWorkOrderId` |
| `ServiceScheduleRepository` | `findByBayIdAndStatusAndScheduledAtBetween`, `findByVehicleId(Pageable)`, `findByScheduledAtBetween(Pageable)` |
| `TechnicianRepository` | `findByActiveTrue`, `countByTechnicianIdAndStatusNotIn` (for WO count) |
| `BayRepository` | `findByActiveTrue` |

### DB Schema (init.sql summary)

All tables use `CREATE TABLE IF NOT EXISTS`. Foreign keys use `ON DELETE RESTRICT`. Enum columns use `VARCHAR(20)`.

```sql
-- vehicles: id, vin UNIQUE, make, model, year, owner_username, deleted, created_at
-- technicians: id, name, active
-- bays: id, name, active
-- work_orders: id, vehicle_id FK, technician_id FK nullable, bay_id FK nullable,
--              status VARCHAR(20), description TEXT, created_at
-- work_order_status_history: id, work_order_id FK, previous_status, new_status,
--                             changed_by, changed_at
-- part_lines: id, work_order_id FK, part_name, quantity INT, unit_cost DECIMAL(10,2)
-- labor_lines: id, work_order_id FK, description, hours DECIMAL(6,2), rate DECIMAL(10,2)
-- service_schedules: id, vehicle_id FK, bay_id FK nullable, scheduled_at DATETIME,
--                    service_type, status VARCHAR(20)
```

Seed data:
```sql
INSERT IGNORE INTO bays (name, active) VALUES ('Bay 1', true), ('Bay 2', true);
INSERT IGNORE INTO technicians (name, active) VALUES ('Admin Technician', true);
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: JWT claims round-trip

*For any* valid username and non-empty list of role strings, building a signed JWT and then parsing it with `JwtUtils` must return the same username and the same set of roles.

**Validates: Requirements 1.1**

### Property 2: Invalid tokens are always rejected

*For any* string that is not a validly-signed JWT (random bytes, truncated tokens, tokens signed with a different key, tokens with a tampered payload), `JwtUtils.validateJwtToken()` must return `false`.

**Validates: Requirements 1.4**

### Property 3: VIN validation accepts exactly 17-character alphanumeric strings

*For any* string, the VIN validator must accept it if and only if it matches `[A-Za-z0-9]{17}` — strings of any other length or containing non-alphanumeric characters must be rejected.

**Validates: Requirements 2.7**

### Property 4: ROLE_CUSTOMER vehicle isolation

*For any* set of vehicles with randomly assigned `ownerUsername` values and any authenticated customer username, the vehicle list endpoint must return only vehicles whose `ownerUsername` matches the authenticated user's username.

**Validates: Requirements 2.8**

### Property 5: Work order state machine — only valid transitions are accepted

*For any* `(fromStatus, toStatus)` pair not present in the allowed-transitions map, `WorkOrderStateMachine.isValidTransition()` must return `false`; for any pair that is present, it must return `true`.

**Validates: Requirements 3.5, 3.6**

### Property 6: Status history is always recorded on transition

*For any* valid status transition on any work order, after the transition completes the status history table must contain exactly one new entry with the correct `previousStatus`, `newStatus`, `changedBy` (matching the JWT subject), and a non-null `changedAt` timestamp.

**Validates: Requirements 3.7**

### Property 7: ROLE_TECHNICIAN write isolation

*For any* set of work orders with randomly assigned technician ids and any authenticated technician, write operations (status transition, add/remove line items) on work orders not assigned to that technician must return HTTP 403.

**Validates: Requirements 3.8**

### Property 8: Closed work order line item rejection

*For any* part line or labor line payload and any work order in `COMPLETED` or `INVOICED` status, all add and delete line item operations must return HTTP 422 with the message "Cannot modify line items on a closed work order".

**Validates: Requirements 4.5**

### Property 9: Total cost calculation correctness

*For any* list of part lines (each with positive `quantity` and `unitCost`) and any list of labor lines (each with positive `hours` and `rate`), `CostCalculator.calculate()` must return exactly `Σ(quantity × unitCost) + Σ(hours × rate)`, with no rounding error beyond two decimal places.

**Validates: Requirements 4.6**

### Property 10: Bay conflict detection

*For any* bay and any two proposed schedule times where `|t1 - t2| < 2 hours`, the second booking attempt must be rejected with HTTP 409; for any two times where `|t1 - t2| >= 2 hours`, the second booking must succeed.

**Validates: Requirements 5.4**

### Property 11: Schedule date range filter correctness

*For any* date range `[start, end]` and any set of schedules with randomly distributed `scheduledAt` values, the filtered list endpoint must return all and only schedules where `scheduledAt` is within `[start, end]`.

**Validates: Requirements 5.2, 5.3**

### Property 12: Datetime UTC round-trip

*For any* UTC `LocalDateTime` value submitted as an ISO 8601 string in a schedule creation request, the value returned by the GET endpoint must represent the same instant (no timezone shift, no precision loss beyond seconds).

**Validates: Requirements 5.6**

---

## Error Handling

### Exception → HTTP Status Mapping

| Exception | HTTP Status | Message |
|---|---|---|
| `ResourceNotFoundException` | 404 | "{Resource} with id {id} not found" |
| `DuplicateVinException` | 409 | "Vehicle with VIN {vin} already exists" |
| `InvalidStatusTransitionException` | 422 | "Invalid status transition from {from} to {to}" |
| `ClosedWorkOrderException` | 422 | "Cannot modify line items on a closed work order" |
| `BayConflictException` | 409 | "Bay is not available for the requested time slot" |
| `MethodArgumentNotValidException` | 400 | Field-level validation errors as array |
| `AccessDeniedException` | 403 | "Access denied" |
| `AuthenticationException` | 401 | Handled by `AuthEntryPointJwt` |

`GlobalExceptionHandler` is a `@RestControllerAdvice` that catches all the above and returns a consistent `ErrorResponse` JSON body:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Invalid status transition from OPEN to COMPLETED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

For `MethodArgumentNotValidException`, the `errors` field is an array of `{field, message}` objects.

### JWT Error Handling

- Missing/malformed/expired JWT: `AuthEntryPointJwt.commence()` returns 401 JSON (copied from user-auth-service pattern).
- Valid JWT but insufficient role: Spring Security returns 403 via `AccessDeniedHandler`.

---

## Testing Strategy

### Dual Testing Approach

Unit tests cover specific examples, edge cases, and error conditions. Property-based tests verify universal properties across many generated inputs. Both are necessary for comprehensive coverage.

### Property-Based Testing Library

**jqwik** (Java property-based testing library, integrates with JUnit 5) is the chosen PBT library. Each property test runs a minimum of **100 iterations**.

Dependency:
```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.1</version>
    <scope>test</scope>
</dependency>
```

Each property test is tagged with a comment referencing the design property:
```java
// Feature: vehicle-maintenance-service, Property 5: Work order state machine — only valid transitions are accepted
@Property(tries = 100)
void invalidTransitionsAreRejected(...) { ... }
```

### Property Tests (one test per property)

| Property | Test class | What varies |
|---|---|---|
| P1: JWT claims round-trip | `JwtUtilsPropertyTest` | username (arbitrary string), roles (list of role strings) |
| P2: Invalid tokens rejected | `JwtUtilsPropertyTest` | random byte arrays, truncated/tampered JWT strings |
| P3: VIN validation | `VinValidatorPropertyTest` | strings of all lengths and character sets |
| P4: Customer vehicle isolation | `VehicleServicePropertyTest` | vehicle lists with random ownerUsername values |
| P5: State machine transitions | `WorkOrderStateMachinePropertyTest` | all (from, to) enum pairs |
| P6: Status history recording | `WorkOrderServicePropertyTest` | valid transition pairs, random actor usernames |
| P7: Technician write isolation | `WorkOrderServicePropertyTest` | work order sets with random technician assignments |
| P8: Closed WO line item rejection | `PartLaborServicePropertyTest` | random part/labor payloads, COMPLETED/INVOICED statuses |
| P9: Cost calculation | `CostCalculatorPropertyTest` | random part lines (qty, unitCost) and labor lines (hours, rate) |
| P10: Bay conflict detection | `BayConflictCheckerPropertyTest` | random time pairs, overlap/non-overlap cases |
| P11: Schedule date range filter | `ScheduleServicePropertyTest` | random date ranges, random schedule distributions |
| P12: Datetime UTC round-trip | `ScheduleServicePropertyTest` | random UTC LocalDateTime values |

### Unit Tests

- `WorkOrderStateMachineTest` — example-based: each valid transition succeeds, each invalid transition fails
- `CostCalculatorTest` — example-based: zero lines = 0.00, single part, single labor, mixed
- `VehicleControllerTest` (MockMvc) — 201 on create, 409 on duplicate VIN, 404 on missing id, 401 on no token
- `WorkOrderControllerTest` (MockMvc) — 201 on create, 422 on invalid transition, 422 on closed WO modification
- `ScheduleControllerTest` (MockMvc) — 201 on create, 409 on bay conflict
- `GlobalExceptionHandlerTest` — each exception type maps to correct HTTP status and body shape

### Integration Tests

- `VehicleRepositoryTest` — soft delete, findByOwnerUsername, duplicate VIN constraint
- `WorkOrderRepositoryTest` — filter by status/vehicleId/technicianId
- `ServiceScheduleRepositoryTest` — findByBayIdAndStatusAndScheduledAtBetween

### Smoke Tests

- `OpenApiSmokeTest` — GET /v3/api-docs returns 200 with BearerAuth security scheme
- `ActuatorSmokeTest` — GET /actuator/health returns 200 without auth token

---

## OpenAPI / Swagger Configuration

`OpenApiConfig.java` defines:

```java
@Bean
public OpenAPI maintenanceServiceOpenAPI() {
    return new OpenAPI()
        .info(new Info().title("Maintenance Service API").version("v1"))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
        .components(new Components().addSecuritySchemes("BearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
}
```

All controllers are annotated with `@Tag` and all endpoints with `@Operation`, `@ApiResponse` annotations to produce complete OpenAPI documentation.

---

## Dockerfile Design

```dockerfile
# Stage 1: build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Environment variables consumed at runtime (all required):

| Variable | Spring property |
|---|---|
| `DB_HOST` | `spring.datasource.url` (interpolated) |
| `DB_PORT` | `spring.datasource.url` (interpolated) |
| `DB_NAME` | `spring.datasource.url` (interpolated) |
| `DB_USERNAME` | `spring.datasource.username` |
| `DB_PASSWORD` | `spring.datasource.password` |
| `JWT_SECRET` | `maintenance.app.jwtSecret` |

---

## application.properties / Environment Variable Mapping

```properties
server.port=8081

# DataSource — all values from environment variables
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:maintenance_db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}

spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

# Jackson — serialize LocalDateTime as ISO 8601 UTC
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=UTC

# JWT — shared secret with user-auth-service
maintenance.app.jwtSecret=${JWT_SECRET}

# Actuator
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never

# OpenAPI
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
```

`ddl-auto=validate` is used in production/Docker; `ddl-auto=create-drop` or `update` can be used in test profiles. The `init.sql` script handles schema creation.

---

## pom.xml Dependencies

```xml
<dependencies>
    <!-- Spring Boot starters -->
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId></dependency>

    <!-- MySQL -->
    <dependency><groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>

    <!-- JWT (same versions as user-auth-service) -->
    <dependency><groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId><version>0.11.5</version></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId><version>0.11.5</version><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId><version>0.11.5</version><scope>runtime</scope></dependency>

    <!-- OpenAPI / Swagger -->
    <dependency><groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.1.0</version></dependency>

    <!-- Test -->
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>net.jqwik</groupId>
        <artifactId>jqwik</artifactId><version>1.8.1</version><scope>test</scope></dependency>
    <dependency><groupId>com.h2database</groupId>
        <artifactId>h2</artifactId><scope>test</scope></dependency>
</dependencies>
```
