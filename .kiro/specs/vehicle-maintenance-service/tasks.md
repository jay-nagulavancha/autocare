# Implementation Plan: Vehicle Maintenance Service

## Overview

Build the `vehicle-maintenance-service` from scratch as a standalone Spring Boot 3.x Maven project under `autocare/vehicle-maintenance-service/`. The service is a JWT resource server (port 8081) that owns all vehicle maintenance business logic: vehicles, work orders, parts/labor lines, service schedules, technicians, and bays. JWT validation is performed locally using a shared HS256 secret — no inter-service calls at runtime.

Implementation language: **Java 17**, package root `com.autocare.maintenance`.

---

## Tasks

- [x] 1. Scaffold Maven project structure
  - Create `autocare/vehicle-maintenance-service/pom.xml` with groupId `com.autocare`, artifactId `vehicle-maintenance-service`, Java 17, Spring Boot 3.x parent
  - Add all dependencies from the design: spring-boot-starter-web, data-jpa, security, validation, actuator, mysql-connector-j, jjwt-api/impl/jackson (0.11.5), springdoc-openapi-starter-webmvc-ui (2.1.0), spring-boot-starter-test, spring-security-test, jqwik (1.8.1), h2 (test scope)
  - Add Maven wrapper files (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`)
  - Create `src/main/java/com/autocare/maintenance/MaintenanceServiceApplication.java` with `@SpringBootApplication`
  - Create `src/main/resources/application.properties` with all properties from the design (port 8081, datasource via env vars, JPA validate, Jackson UTC, JWT secret, actuator health, springdoc paths)
  - Create `src/test/resources/application-test.properties` with H2 in-memory datasource and `ddl-auto=create-drop`
  - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [x] 2. Implement JWT security layer
  - [x] 2.1 Create `security/jwt/JwtUtils.java`
    - Implement `validateJwtToken(String jwt)` → boolean (HS256 signature + expiry check using `JWT_SECRET` env var via `@Value("${maintenance.app.jwtSecret}")`)
    - Implement `getUserNameFromJwtToken(String jwt)` → String (subject claim)
    - Implement `getRolesFromJwtToken(String jwt)` → `List<GrantedAuthority>` (reads `roles` claim embedded by user-auth-service)
    - No token generation methods — resource server only
    - _Requirements: 1.1, 1.5_

  - [ ]* 2.2 Write property test for JWT claims round-trip (Property 1)
    - **Property 1: JWT claims round-trip**
    - For any valid username and non-empty list of role strings, a signed JWT parsed by `JwtUtils` must return the same username and same set of roles
    - Class: `JwtUtilsPropertyTest`, annotation: `@Property(tries = 100)`
    - Comment: `// Feature: vehicle-maintenance-service, Property 1: JWT claims round-trip`
    - **Validates: Requirements 1.1**

  - [ ]* 2.3 Write property test for invalid token rejection (Property 2)
    - **Property 2: Invalid tokens are always rejected**
    - For any string that is not a validly-signed JWT, `validateJwtToken()` must return false
    - Class: `JwtUtilsPropertyTest`, vary: random byte arrays, truncated/tampered strings, tokens signed with wrong key
    - Comment: `// Feature: vehicle-maintenance-service, Property 2: Invalid tokens are always rejected`
    - **Validates: Requirements 1.4**

  - [x] 2.4 Create `security/jwt/AuthEntryPointJwt.java`
    - Implement `AuthenticationEntryPoint.commence()` returning 401 JSON body (copied/adapted from user-auth-service pattern)
    - _Requirements: 1.2, 1.3, 1.4_

  - [x] 2.5 Create `security/jwt/AuthTokenFilter.java`
    - Implement `OncePerRequestFilter.doFilterInternal()`: parse Bearer token → `JwtUtils.validateJwtToken()` → extract username + roles → set `SecurityContextHolder` authentication
    - No `UserDetailsService` or DB lookup — roles come directly from JWT claims
    - _Requirements: 1.1, 1.6_

  - [x] 2.6 Create `security/services/JwtUserDetails.java`
    - Lightweight `UserDetails` implementation holding username and `GrantedAuthority` list extracted from JWT claims (no DB)
    - _Requirements: 1.1_

  - [x] 2.7 Create `security/WebSecurityConfig.java`
    - Configure stateless session, `AuthTokenFilter` as filter, `AuthEntryPointJwt` as entry point
    - Permit `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` without auth
    - Require authentication for all `/api/v1/**`
    - Enable `@EnableMethodSecurity` for `@PreAuthorize` support
    - _Requirements: 1.6, 1.7_

- [ ] 3. Checkpoint — verify security layer compiles and actuator health returns 200
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement JPA entities and repositories
  - [x] 4.1 Create `model/WorkOrderStatus.java` enum
    - Values: `OPEN, IN_PROGRESS, PENDING_PARTS, COMPLETED, INVOICED`
    - _Requirements: 3.6_

  - [x] 4.2 Create all JPA entity classes
    - `model/Vehicle.java` — id, vin (unique, length 17), make, model, year, ownerUsername, deleted=false, createdAt
    - `model/Technician.java` — id, name, active=true
    - `model/Bay.java` — id, name, active=true
    - `model/WorkOrder.java` — id, @ManyToOne Vehicle, @ManyToOne Technician (nullable), @ManyToOne Bay (nullable), status (EnumType.STRING, default OPEN), description, createdAt, @OneToMany partLines/laborLines/statusHistory (cascade ALL)
    - `model/WorkOrderStatusHistory.java` — id, @ManyToOne WorkOrder, previousStatus, newStatus, changedBy, changedAt
    - `model/PartLine.java` — id, @ManyToOne WorkOrder, partName, quantity (@Min(1)), unitCost (@DecimalMin("0.01"))
    - `model/LaborLine.java` — id, @ManyToOne WorkOrder, description, hours (@DecimalMin("0.01")), rate (@DecimalMin("0.01"))
    - `model/ServiceSchedule.java` — id, @ManyToOne Vehicle, @ManyToOne Bay (optional), scheduledAt (UTC LocalDateTime), serviceType, status (CONFIRMED/CANCELLED)
    - _Requirements: 2.1, 3.1, 4.1, 4.2, 5.1, 6.1_

  - [x] 4.3 Create all Spring Data JPA repository interfaces
    - `VehicleRepository` — `findByVin`, `findByOwnerUsernameAndDeletedFalse`, `findAllByDeletedFalse(Pageable)`
    - `WorkOrderRepository` — dynamic filter query by status/vehicleId/technicianId with Pageable
    - `WorkOrderStatusHistoryRepository` — `findByWorkOrderIdOrderByChangedAtDesc`
    - `PartLineRepository` — `findByWorkOrderId`
    - `LaborLineRepository` — `findByWorkOrderId`
    - `ServiceScheduleRepository` — `findByBayIdAndStatusAndScheduledAtBetween`, `findByVehicleId(Pageable)`, `findByScheduledAtBetween(Pageable)`
    - `TechnicianRepository` — `findByActiveTrue`
    - `BayRepository` — `findByActiveTrue`
    - _Requirements: 2.3, 2.4, 3.3, 5.2, 5.3, 6.1, 6.2_

  - [ ]* 4.4 Write integration tests for VehicleRepository
    - Test soft delete (deleted flag set, not returned by `findAllByDeletedFalse`), `findByOwnerUsername`, duplicate VIN constraint violation
    - Class: `VehicleRepositoryTest` with `@DataJpaTest` + H2
    - _Requirements: 2.6, 2.8_

  - [ ]* 4.5 Write integration tests for WorkOrderRepository and ServiceScheduleRepository
    - Test filter by status/vehicleId/technicianId; test `findByBayIdAndStatusAndScheduledAtBetween`
    - Classes: `WorkOrderRepositoryTest`, `ServiceScheduleRepositoryTest`
    - _Requirements: 3.3, 5.2, 5.3_

- [x] 5. Implement pure-logic service components
  - [x] 5.1 Create `service/WorkOrderStateMachine.java`
    - Encode allowed-transitions map: `OPEN→[IN_PROGRESS]`, `IN_PROGRESS→[PENDING_PARTS, COMPLETED]`, `PENDING_PARTS→[IN_PROGRESS]`, `COMPLETED→[INVOICED]`, `INVOICED→[]`
    - Implement `isValidTransition(WorkOrderStatus from, WorkOrderStatus to)` → boolean
    - Implement `allowedNext(WorkOrderStatus from)` → `Set<WorkOrderStatus>`
    - _Requirements: 3.5, 3.6_

  - [ ]* 5.2 Write property test for state machine transitions (Property 5)
    - **Property 5: Work order state machine — only valid transitions are accepted**
    - For every `(from, to)` enum pair not in the allowed map, `isValidTransition` must return false; for every pair in the map, must return true
    - Class: `WorkOrderStateMachinePropertyTest`, vary: all enum combinations
    - Comment: `// Feature: vehicle-maintenance-service, Property 5: Work order state machine — only valid transitions are accepted`
    - **Validates: Requirements 3.5, 3.6**

  - [ ]* 5.3 Write unit tests for WorkOrderStateMachine
    - Example-based: each valid transition succeeds, each invalid transition fails, terminal state INVOICED has no next states
    - Class: `WorkOrderStateMachineTest`
    - _Requirements: 3.5, 3.6_

  - [x] 5.4 Create `service/CostCalculator.java`
    - Implement `calculate(List<PartLine> parts, List<LaborLine> labors)` → BigDecimal
    - Formula: `Σ(quantity × unitCost) + Σ(hours × rate)`, no rounding beyond 2 decimal places
    - _Requirements: 4.6_

  - [ ]* 5.5 Write property test for cost calculation (Property 9)
    - **Property 9: Total cost calculation correctness**
    - For any list of part lines (positive qty, unitCost) and labor lines (positive hours, rate), result must equal exact arithmetic sum with no rounding error beyond 2 decimal places
    - Class: `CostCalculatorPropertyTest`, vary: random positive BigDecimal values
    - Comment: `// Feature: vehicle-maintenance-service, Property 9: Total cost calculation correctness`
    - **Validates: Requirements 4.6**

  - [ ]* 5.6 Write unit tests for CostCalculator
    - Cases: zero lines = 0.00, single part only, single labor only, mixed lines
    - Class: `CostCalculatorTest`
    - _Requirements: 4.6_

  - [x] 5.7 Create `service/BayConflictChecker.java`
    - Implement `hasConflict(Long bayId, LocalDateTime proposed, Long excludeScheduleId, ServiceScheduleRepository repo)` → boolean
    - Conflict window: `|proposed - existing| < 2 hours` for any CONFIRMED schedule on the same bay
    - _Requirements: 5.4_

  - [ ]* 5.8 Write property test for bay conflict detection (Property 10)
    - **Property 10: Bay conflict detection**
    - For any two times where `|t1 - t2| < 2 hours`, second booking must be rejected (409); for `|t1 - t2| >= 2 hours`, second booking must succeed
    - Class: `BayConflictCheckerPropertyTest`, vary: random time pairs in both overlap and non-overlap ranges
    - Comment: `// Feature: vehicle-maintenance-service, Property 10: Bay conflict detection`
    - **Validates: Requirements 5.4**

- [ ] 6. Checkpoint — ensure pure-logic components compile and unit/property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement payload DTOs and exception classes
  - [x] 7.1 Create request DTOs in `payload/request/`
    - `CreateVehicleRequest` — vin (@Pattern [A-Za-z0-9]{17}), make, model, year, ownerUsername
    - `UpdateVehicleRequest` — make, model, year, ownerUsername
    - `CreateWorkOrderRequest` — vehicleId, description
    - `StatusTransitionRequest` — targetStatus (WorkOrderStatus)
    - `AssignRequest` — technicianId, bayId
    - `AddPartLineRequest` — partName, quantity (@Min(1)), unitCost (@DecimalMin("0.01"))
    - `AddLaborLineRequest` — description, hours (@DecimalMin("0.01")), rate (@DecimalMin("0.01"))
    - `CreateScheduleRequest` — vehicleId, bayId (nullable), scheduledAt (ISO 8601 UTC), serviceType
    - `CreateTechnicianRequest` — name
    - `CreateBayRequest` — name
    - _Requirements: 2.1, 2.5, 2.7, 3.1, 3.4, 4.1, 4.2, 5.1, 6.5_

  - [x] 7.2 Create response DTOs in `payload/response/`
    - `VehicleResponse`, `WorkOrderResponse` (includes partLines, laborLines, statusHistory, totalCost), `PartLineResponse`, `LaborLineResponse`, `ScheduleResponse`, `TechnicianResponse` (includes workOrderCount), `BayResponse`, `ErrorResponse` (status, error, message, timestamp, optional errors array)
    - _Requirements: 2.3, 3.2, 4.6, 5.2, 6.1, 6.2_

  - [x] 7.3 Create exception classes in `exception/`
    - `ResourceNotFoundException`, `DuplicateVinException`, `InvalidStatusTransitionException`, `ClosedWorkOrderException`, `BayConflictException`
    - Create `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping each exception to its HTTP status and `ErrorResponse` body per the design's exception-to-status table
    - Handle `MethodArgumentNotValidException` → 400 with field-level errors array
    - Handle `AccessDeniedException` → 403, `AuthenticationException` → 401 (via `AuthEntryPointJwt`)
    - _Requirements: 2.2, 2.7, 3.5, 4.5, 5.4_

  - [ ]* 7.4 Write unit tests for GlobalExceptionHandler
    - Each exception type maps to correct HTTP status and body shape
    - Class: `GlobalExceptionHandlerTest`
    - _Requirements: 2.2, 3.5, 4.5, 5.4_

- [x] 8. Implement VIN validator and vehicle service + controller
  - [x] 8.1 Add VIN validation via `@Pattern(regexp = "[A-Za-z0-9]{17}")` on `CreateVehicleRequest.vin`
    - Ensure `GlobalExceptionHandler` returns 400 with descriptive field error on violation
    - _Requirements: 2.7_

  - [ ]* 8.2 Write property test for VIN validation (Property 3)
    - **Property 3: VIN validation accepts exactly 17-character alphanumeric strings**
    - For any string, validator must accept iff it matches `[A-Za-z0-9]{17}`; all other lengths or non-alphanumeric chars must be rejected
    - Class: `VinValidatorPropertyTest`, vary: strings of all lengths and character sets
    - Comment: `// Feature: vehicle-maintenance-service, Property 3: VIN validation accepts exactly 17-character alphanumeric strings`
    - **Validates: Requirements 2.7**

  - [x] 8.3 Create `service/VehicleService.java`
    - `createVehicle(CreateVehicleRequest, String actorUsername)` — check duplicate VIN (409), persist, return `VehicleResponse`
    - `getVehicle(Long id)` — 404 if not found or deleted
    - `listVehicles(Pageable, String authenticatedUsername, String role)` — if ROLE_CUSTOMER filter by ownerUsername
    - `updateVehicle(Long id, UpdateVehicleRequest)` — 404 if not found
    - `deleteVehicle(Long id)` — soft delete (set deleted=true), 404 if not found
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.8_

  - [ ]* 8.4 Write property test for ROLE_CUSTOMER vehicle isolation (Property 4)
    - **Property 4: ROLE_CUSTOMER vehicle isolation**
    - For any set of vehicles with random ownerUsername values and any authenticated customer username, list must return only vehicles matching that username
    - Class: `VehicleServicePropertyTest`, vary: vehicle lists with random ownerUsername assignments
    - Comment: `// Feature: vehicle-maintenance-service, Property 4: ROLE_CUSTOMER vehicle isolation`
    - **Validates: Requirements 2.8**

  - [x] 8.5 Create `controller/VehicleController.java`
    - Map all 5 vehicle endpoints per API contract (GET list, POST create, GET by id, PUT update, DELETE soft-delete)
    - Apply `@PreAuthorize`: POST/PUT → `hasAnyRole('ADMIN','TECHNICIAN')`, DELETE → `hasRole('ADMIN')`, GET → any authenticated
    - Annotate with `@Tag`, `@Operation`, `@ApiResponse` for OpenAPI
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ]* 8.6 Write MockMvc unit tests for VehicleController
    - 201 on create, 409 on duplicate VIN, 400 on invalid VIN, 404 on missing id, 401 on no token, 403 on wrong role
    - Class: `VehicleControllerTest`
    - _Requirements: 2.1, 2.2, 2.7_

- [x] 9. Implement work order service + controller
  - [x] 9.1 Create `service/WorkOrderService.java`
    - `createWorkOrder(CreateWorkOrderRequest, String actorUsername)` — vehicle must exist (404), create with status OPEN
    - `getWorkOrder(Long id)` — 404 if not found, include partLines, laborLines, statusHistory, totalCost via `CostCalculator`
    - `listWorkOrders(Pageable, WorkOrderStatus, Long vehicleId, Long technicianId)` — all filters optional
    - `transitionStatus(Long id, WorkOrderStatus target, String actorUsername)` — validate via `WorkOrderStateMachine` (422 on invalid), persist new status, record `WorkOrderStatusHistory` entry
    - `assignTechnicianAndBay(Long id, AssignRequest)` — technician must exist (404), bay must exist (404)
    - Enforce ROLE_TECHNICIAN write isolation: if actor is TECHNICIAN and work order not assigned to them → 403
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8, 6.3, 6.4_

  - [ ]* 9.2 Write property test for status history recording (Property 6)
    - **Property 6: Status history is always recorded on transition**
    - For any valid status transition, after completion the history table must contain exactly one new entry with correct previousStatus, newStatus, changedBy (JWT subject), and non-null changedAt
    - Class: `WorkOrderServicePropertyTest`, vary: valid transition pairs, random actor usernames
    - Comment: `// Feature: vehicle-maintenance-service, Property 6: Status history is always recorded on transition`
    - **Validates: Requirements 3.7**

  - [ ]* 9.3 Write property test for ROLE_TECHNICIAN write isolation (Property 7)
    - **Property 7: ROLE_TECHNICIAN write isolation**
    - For any set of work orders with random technician assignments and any authenticated technician, write operations on unassigned work orders must return 403
    - Class: `WorkOrderServicePropertyTest`, vary: work order sets with random technician id assignments
    - Comment: `// Feature: vehicle-maintenance-service, Property 7: ROLE_TECHNICIAN write isolation`
    - **Validates: Requirements 3.8**

  - [x] 9.4 Create `controller/WorkOrderController.java`
    - Map all 5 work order endpoints per API contract
    - Apply `@PreAuthorize`: POST/PATCH → `hasAnyRole('ADMIN','TECHNICIAN')`, PUT assign → `hasRole('ADMIN')`, GET → any authenticated
    - Annotate with `@Tag`, `@Operation`, `@ApiResponse`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 6.3_

  - [ ]* 9.5 Write MockMvc unit tests for WorkOrderController
    - 201 on create, 422 on invalid transition, 422 on closed WO modification, 403 on technician isolation
    - Class: `WorkOrderControllerTest`
    - _Requirements: 3.1, 3.5, 4.5_

- [x] 10. Implement parts and labor service + controller
  - [x] 10.1 Create `service/PartLaborService.java`
    - `addPartLine(Long workOrderId, AddPartLineRequest)` — 404 if WO not found, 422 if COMPLETED/INVOICED (`ClosedWorkOrderException`)
    - `removePartLine(Long workOrderId, Long partLineId)` — 404 if not found, 422 if closed
    - `addLaborLine(Long workOrderId, AddLaborLineRequest)` — same guards
    - `removeLaborLine(Long workOrderId, Long laborLineId)` — same guards
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 10.2 Write property test for closed work order line item rejection (Property 8)
    - **Property 8: Closed work order line item rejection**
    - For any part/labor payload and any WO in COMPLETED or INVOICED status, all add/delete operations must return 422 with message "Cannot modify line items on a closed work order"
    - Class: `PartLaborServicePropertyTest`, vary: random part/labor payloads, both closed statuses
    - Comment: `// Feature: vehicle-maintenance-service, Property 8: Closed work order line item rejection`
    - **Validates: Requirements 4.5**

  - [x] 10.3 Wire parts and labor endpoints into `WorkOrderController`
    - POST `/api/v1/work-orders/{id}/parts` → 201, DELETE `/api/v1/work-orders/{id}/parts/{partLineId}` → 204
    - POST `/api/v1/work-orders/{id}/labor` → 201, DELETE `/api/v1/work-orders/{id}/labor/{laborLineId}` → 204
    - Annotate with `@Operation`, `@ApiResponse`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 11. Checkpoint — ensure work order and parts/labor tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Implement schedule service + controller
  - [x] 12.1 Create `service/ScheduleService.java`
    - `createSchedule(CreateScheduleRequest, String actorUsername)` — vehicle must exist (404); if bayId provided, call `BayConflictChecker.hasConflict()` (409 on conflict); persist with status CONFIRMED
    - `listSchedules(Pageable, LocalDateTime start, LocalDateTime end)` — optional date range filter
    - `listSchedulesByVehicle(Long vehicleId, Pageable)` — 404 if vehicle not found
    - `cancelSchedule(Long id)` — set status CANCELLED, 404 if not found
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [ ]* 12.2 Write property test for schedule date range filter (Property 11)
    - **Property 11: Schedule date range filter correctness**
    - For any `[start, end]` range and any set of schedules with random scheduledAt values, filtered list must return all and only schedules where `scheduledAt` is within `[start, end]`
    - Class: `ScheduleServicePropertyTest`, vary: random date ranges, random schedule distributions
    - Comment: `// Feature: vehicle-maintenance-service, Property 11: Schedule date range filter correctness`
    - **Validates: Requirements 5.2, 5.3**

  - [ ]* 12.3 Write property test for datetime UTC round-trip (Property 12)
    - **Property 12: Datetime UTC round-trip**
    - For any UTC LocalDateTime submitted as ISO 8601 in a schedule creation request, the GET response must represent the same instant (no timezone shift, no precision loss beyond seconds)
    - Class: `ScheduleServicePropertyTest`, vary: random UTC LocalDateTime values
    - Comment: `// Feature: vehicle-maintenance-service, Property 12: Datetime UTC round-trip`
    - **Validates: Requirements 5.6**

  - [x] 12.4 Create `controller/ScheduleController.java`
    - Map all 4 schedule endpoints per API contract (GET list with optional date range, GET by vehicleId, POST create, DELETE cancel)
    - Require authentication for all; no role restriction beyond authenticated
    - Annotate with `@Tag`, `@Operation`, `@ApiResponse`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 12.5 Write MockMvc unit tests for ScheduleController
    - 201 on create, 409 on bay conflict, 404 on unknown vehicle
    - Class: `ScheduleControllerTest`
    - _Requirements: 5.1, 5.4_

- [x] 13. Implement technician and bay services + controllers
  - [x] 13.1 Create `service/TechnicianService.java`
    - `listTechnicians()` — return active technicians with current open work order count
    - `createTechnician(CreateTechnicianRequest)` → 201
    - `updateTechnician(Long id, CreateTechnicianRequest)` → 200, 404 if not found
    - `deactivateTechnician(Long id)` → set active=false, 204, 404 if not found
    - _Requirements: 6.1, 6.5_

  - [x] 13.2 Create `service/BayService.java`
    - `listBays()` — return all bays with availability (no CONFIRMED schedule in next 2 hours)
    - `createBay(CreateBayRequest)` → 201
    - `updateBay(Long id, CreateBayRequest)` → 200, 404 if not found
    - `deactivateBay(Long id)` → set active=false, 204, 404 if not found
    - _Requirements: 6.2, 6.5_

  - [x] 13.3 Create `controller/TechnicianController.java` and `controller/BayController.java`
    - Map all CRUD endpoints per API contract
    - Apply `@PreAuthorize`: POST/PUT/DELETE → `hasRole('ADMIN')`, GET → any authenticated
    - Annotate with `@Tag`, `@Operation`, `@ApiResponse`
    - _Requirements: 6.1, 6.2, 6.5_

- [x] 14. Implement OpenAPI configuration and smoke tests
  - [x] 14.1 Create `config/OpenApiConfig.java`
    - Define `OpenAPI` bean with title "Maintenance Service API", version "v1"
    - Add `BearerAuth` security scheme (HTTP bearer, JWT format) and global security requirement
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 14.2 Write OpenAPI and actuator smoke tests
    - `OpenApiSmokeTest` — GET `/v3/api-docs` returns 200 with BearerAuth security scheme present
    - `ActuatorSmokeTest` — GET `/actuator/health` returns 200 without auth token
    - _Requirements: 7.1, 7.2, 1.7_

- [-] 15. Create database bootstrap script and Dockerfile
  - [x] 15.1 Create `autocare/db/vehicle-maintenance-service/init.sql`
    - All 8 tables using `CREATE TABLE IF NOT EXISTS` with correct columns, types, FK constraints (`ON DELETE RESTRICT`), enum columns as `VARCHAR(20)`
    - Seed: `INSERT IGNORE INTO bays (name, active) VALUES ('Bay 1', true), ('Bay 2', true)`
    - Seed: `INSERT IGNORE INTO technicians (name, active) VALUES ('Admin Technician', true)`
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 15.2 Create `autocare/vehicle-maintenance-service/Dockerfile`
    - Multi-stage build: stage 1 `eclipse-temurin:17-jdk-alpine` — copy mvnw + pom, run `dependency:go-offline`, copy src, run `mvnw clean package -DskipTests`
    - Stage 2 `eclipse-temurin:17-jre-alpine` — copy JAR, `EXPOSE 8081`, `ENTRYPOINT ["java", "-jar", "app.jar"]`
    - _Requirements: 9.2, 9.4_

- [ ] 16. Final checkpoint — full test suite passes
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- All 12 correctness properties from the design are covered by property test sub-tasks (P1–P12)
- Property tests use jqwik with `@Property(tries = 100)` minimum
- Each property test comment references the design property number and feature name for traceability
- Checkpoints at tasks 3, 6, 11, and 16 ensure incremental validation
- `ddl-auto=validate` in production; H2 + `create-drop` in test profile
- All secrets via environment variables — no hard-coded credentials anywhere
