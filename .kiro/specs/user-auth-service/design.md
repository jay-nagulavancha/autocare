# Design Document — Auth Service

## Overview

The Auth Service is a standalone Spring Boot 3.x application responsible for identity and access management in the Vehicle Maintenance System. It issues signed JWTs that downstream services (vehicle-maintenance-service) validate locally without calling back to this service at runtime.

**Responsibilities:**
- User registration with role assignment
- Credential validation and JWT issuance
- Role seeding via idempotent SQL bootstrap
- Containerised deployment via Docker

**Out of scope:** The Auth Service never validates JWTs on behalf of other services, never manages vehicles or work orders, and never shares database tables with any other service.

**Key design constraints already locked:**
- Spring Boot 3.1.x, Spring Security 6, jjwt 0.11.5, Spring Data JPA, MySQL
- HS256 JWT signing; secret read exclusively from `JWT_SECRET` environment variable
- Stateless sessions — no HTTP session state
- Port 8080

---

## Architecture

### Request Flow

```
Client
  │
  ├─ POST /api/auth/signup ──► AuthController.registerUser()
  │                                 │
  │                                 ├─ UserRepository.existsByUsername()
  │                                 ├─ UserRepository.existsByEmail()
  │                                 ├─ PasswordEncoder.encode()
  │                                 ├─ RoleRepository.findByName()
  │                                 └─ UserRepository.save()
  │
  └─ POST /api/auth/signin ──► AuthController.authenticateUser()
                                    │
                                    ├─ AuthenticationManager.authenticate()
                                    │       └─ UserDetailsServiceImpl.loadUserByUsername()
                                    │               └─ UserRepository.findByUsername()
                                    ├─ JwtUtils.generateJwtToken()
                                    └─ ResponseEntity<JwtResponse>
```

### Per-Request JWT Validation Flow (protected endpoints)

```
Incoming request
  │
  └─ AuthTokenFilter.doFilterInternal()
          │
          ├─ parseJwt()  ← strips "Bearer " prefix from Authorization header
          ├─ JwtUtils.validateJwtToken()
          ├─ JwtUtils.getUserNameFromJwtToken()
          ├─ UserDetailsServiceImpl.loadUserByUsername()
          └─ SecurityContextHolder.setAuthentication()
```

### Security Filter Chain

```
Request
  └─ AuthTokenFilter (OncePerRequestFilter)
       └─ Spring Security filter chain
            ├─ /api/auth/**  → permitAll (no token required)
            ├─ /api/test/**  → permitAll
            └─ any other    → authenticated
```

Unauthenticated requests to protected paths are handled by `AuthEntryPointJwt`, which returns a structured JSON 401 response instead of the default HTML error page.

---

## Components and Interfaces

### Package Structure

```
com.bezkoder.springjwt
├── SpringBootSecurityJwtApplication.java   ← @SpringBootApplication entry point
│
├── controllers/
│   └── AuthController.java                 ← POST /api/auth/signup, /signin
│
├── models/
│   ├── ERole.java                          ← enum: ROLE_ADMIN, ROLE_TECHNICIAN, ROLE_CUSTOMER
│   ├── Role.java                           ← @Entity → roles table
│   └── User.java                           ← @Entity → users table (ManyToMany → roles)
│
├── payload/
│   ├── request/
│   │   ├── LoginRequest.java               ← username, password (@NotBlank)
│   │   └── SignupRequest.java              ← username, email, password, role set
│   └── response/
│       ├── JwtResponse.java                ← token, type, id, username, email, roles
│       └── MessageResponse.java            ← message string
│
├── repository/
│   ├── UserRepository.java                 ← JpaRepository<User,Long>
│   └── RoleRepository.java                 ← JpaRepository<Role,Long>
│
└── security/
    ├── WebSecurityConfig.java              ← SecurityFilterChain, DaoAuthenticationProvider
    ├── jwt/
    │   ├── AuthEntryPointJwt.java          ← AuthenticationEntryPoint → JSON 401
    │   ├── AuthTokenFilter.java            ← OncePerRequestFilter, JWT extraction
    │   └── JwtUtils.java                   ← generate / validate / parse JWT
    └── services/
        ├── UserDetailsImpl.java            ← UserDetails adapter over User entity
        └── UserDetailsServiceImpl.java     ← UserDetailsService → loads by username
```

### Component Responsibilities

**AuthController**
- `POST /api/auth/signup` — validates uniqueness, BCrypt-encodes password, resolves roles from the `role` string set (`"admin"` → `ROLE_ADMIN`, `"technician"` → `ROLE_TECHNICIAN`, `"customer"` → `ROLE_CUSTOMER`; default when omitted → `ROLE_CUSTOMER`), persists user, returns 200 `MessageResponse`.
- `POST /api/auth/signin` — delegates to `AuthenticationManager`, calls `JwtUtils.generateJwtToken()`, returns 200 `JwtResponse`.

> **Note:** The existing `ERole` enum and `AuthController` switch still reference `ROLE_USER` / `ROLE_MODERATOR`. These must be updated to `ROLE_ADMIN` / `ROLE_TECHNICIAN` / `ROLE_CUSTOMER` to match the system role model.

**JwtUtils**
- `generateJwtToken(Authentication)` — builds a JWT with subject = username, `iat` = now, `exp` = now + `jwtExpirationMs`, signed with HS256 using the Base64-decoded `jwtSecret`.
- `validateJwtToken(String)` — parses and verifies signature; catches and logs `MalformedJwtException`, `ExpiredJwtException`, `UnsupportedJwtException`, `IllegalArgumentException`; returns boolean.
- `getUserNameFromJwtToken(String)` — extracts the `sub` claim.
- `key()` — private helper; decodes `jwtSecret` from Base64 and returns an `HmacSHA256` key via `Keys.hmacShaKeyFor`.

**AuthTokenFilter** (`OncePerRequestFilter`)
- Extracts the raw JWT from the `Authorization: Bearer <token>` header.
- Calls `JwtUtils.validateJwtToken()`; on success loads `UserDetails` and sets `SecurityContextHolder` authentication.
- Any exception is caught and logged; the filter chain continues regardless (unauthenticated requests are handled downstream by the security config).

**WebSecurityConfig**
- Disables CSRF (stateless REST API).
- Sets session creation policy to `STATELESS`.
- Registers `AuthEntryPointJwt` as the authentication entry point.
- Permits `/api/auth/**` and `/api/test/**` without authentication; all other paths require authentication.
- Registers `DaoAuthenticationProvider` (backed by `UserDetailsServiceImpl` + `BCryptPasswordEncoder`).
- Inserts `AuthTokenFilter` before `UsernamePasswordAuthenticationFilter`.

**UserDetailsImpl**
- Wraps a `User` entity as a Spring Security `UserDetails`.
- `build(User)` static factory maps `Role` entities to `SimpleGrantedAuthority` using the enum name (e.g. `"ROLE_ADMIN"`).
- `equals` / `hashCode` based on `id` only.

**UserDetailsServiceImpl**
- Implements `UserDetailsService`.
- `loadUserByUsername(String)` — queries `UserRepository.findByUsername()`, throws `UsernameNotFoundException` if absent, delegates to `UserDetailsImpl.build()`.

---

## Data Models

### Entity: User

```
Table: users
┌─────────────┬──────────────┬──────────────────────────────────────┐
│ Column      │ Type         │ Constraints                          │
├─────────────┼──────────────┼──────────────────────────────────────┤
│ id          │ BIGINT       │ PK, AUTO_INCREMENT                   │
│ username    │ VARCHAR(20)  │ NOT NULL, UNIQUE                     │
│ email       │ VARCHAR(50)  │ NOT NULL, UNIQUE                     │
│ password    │ VARCHAR(120) │ NOT NULL  (BCrypt hash)              │
└─────────────┴──────────────┴──────────────────────────────────────┘
```

Java: `@Entity @Table(name="users")` with `@UniqueConstraint` on `username` and `email`. `@ManyToMany(fetch=LAZY)` to `Role` via join table `user_roles`.

### Entity: Role

```
Table: roles
┌────────┬─────────────┬──────────────────────────────────────────┐
│ Column │ Type        │ Constraints                              │
├────────┼─────────────┼──────────────────────────────────────────┤
│ id     │ INT         │ PK, AUTO_INCREMENT                       │
│ name   │ VARCHAR(20) │ NOT NULL  (@Enumerated(STRING))          │
└────────┴─────────────┴──────────────────────────────────────────┘
```

Seed rows: `ROLE_ADMIN`, `ROLE_TECHNICIAN`, `ROLE_CUSTOMER`.

### Join Table: user_roles

```
Table: user_roles
┌─────────┬────────┬──────────────────────────────┐
│ Column  │ Type   │ Constraints                  │
├─────────┼────────┼──────────────────────────────┤
│ user_id │ BIGINT │ FK → users(id)               │
│ role_id │ INT    │ FK → roles(id)               │
└─────────┴────────┴──────────────────────────────┘
```

### Enum: ERole

```java
public enum ERole {
  ROLE_ADMIN,
  ROLE_TECHNICIAN,
  ROLE_CUSTOMER
}
```

### Request / Response Payloads

**LoginRequest**
```json
{ "username": "john", "password": "secret" }
```
Constraints: both fields `@NotBlank`.

**SignupRequest**
```json
{ "username": "john", "email": "john@example.com", "password": "secret", "role": ["admin"] }
```
Constraints: `username` 3–20 chars, `email` valid format max 50, `password` 6–40 chars, `role` optional.

**JwtResponse** (sign-in success)
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

**MessageResponse** (sign-up success or error)
```json
{ "message": "User registered successfully!" }
```

### application.properties / Environment Variable Mapping

The `application.properties` file uses Spring's `${ENV_VAR:default}` syntax so that environment variables override defaults at runtime:

```properties
# Datasource — all values injected from environment
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:auth_db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}

spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update

# JWT — secret and expiry injected from environment; no defaults committed
bezkoder.app.jwtSecret=${JWT_SECRET}
bezkoder.app.jwtExpirationMs=${JWT_EXPIRATION_MS:86400000}
```

`JWT_SECRET` has no default — the application will fail to start if it is absent, which is the desired behaviour (fail-fast over silent misconfiguration).

### Dockerfile Design

Multi-stage build to keep the final image small:

```dockerfile
# Stage 1 — build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2 — runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Runtime environment variables expected by the container:

| Variable           | Required | Description                          |
|--------------------|----------|--------------------------------------|
| `JWT_SECRET`       | Yes      | Base64-encoded HS256 signing key     |
| `JWT_EXPIRATION_MS`| No       | Token TTL ms (default 86400000)      |
| `DB_HOST`          | Yes      | MySQL hostname                       |
| `DB_PORT`          | No       | MySQL port (default 3306)            |
| `DB_NAME`          | Yes      | Schema name (e.g. `auth_db`)         |
| `DB_USERNAME`      | Yes      | MySQL user                           |
| `DB_PASSWORD`      | Yes      | MySQL password                       |

### DB Init Script — db/user-auth-service/init.sql

```sql
CREATE DATABASE IF NOT EXISTS auth_db;
USE auth_db;

CREATE TABLE IF NOT EXISTS roles (
  id   INT          NOT NULL AUTO_INCREMENT,
  name VARCHAR(20)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS users (
  id       BIGINT       NOT NULL AUTO_INCREMENT,
  username VARCHAR(20)  NOT NULL,
  email    VARCHAR(50)  NOT NULL,
  password VARCHAR(120) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_email    (email)
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id INT    NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- Idempotent role seed
INSERT IGNORE INTO roles (name) VALUES ('ROLE_ADMIN');
INSERT IGNORE INTO roles (name) VALUES ('ROLE_TECHNICIAN');
INSERT IGNORE INTO roles (name) VALUES ('ROLE_CUSTOMER');
```

`CREATE TABLE IF NOT EXISTS` and `INSERT IGNORE` make the script safe to re-run on every container start.

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: JWT subject round-trip

*For any* registered username, a JWT generated by `JwtUtils.generateJwtToken()` for that user must yield the same username when parsed back by `JwtUtils.getUserNameFromJwtToken()`.

**Validates: Requirements 2.1, 2.3**

### Property 2: JWT expiry is bounded

*For any* JWT generated by `JwtUtils.generateJwtToken()`, the `exp` claim must be strictly greater than the `iat` claim and must equal `iat + jwtExpirationMs` (within a 1-second tolerance for clock ticks during test execution).

**Validates: Requirements 2.3**

### Property 3: Expired tokens are rejected

*For any* JWT whose expiry has passed (i.e. `exp` is in the past), `JwtUtils.validateJwtToken()` must return `false`.

**Validates: Requirements 2.3**

### Property 4: Tampered tokens are rejected

*For any* valid JWT, modifying any single character in the signature segment must cause `JwtUtils.validateJwtToken()` to return `false`.

**Validates: Requirements 2.3, 2.4**

### Property 5: Duplicate username registration is rejected

*For any* username that already exists in the system, a signup attempt with that username must return HTTP 400 with the message `"Error: Username is already taken!"` regardless of the email or password supplied.

**Validates: Requirements 1.2**

### Property 6: Duplicate email registration is rejected

*For any* email address that already exists in the system, a signup attempt with that email must return HTTP 400 with the message `"Error: Email is already in use!"` regardless of the username or password supplied.

**Validates: Requirements 1.3**

### Property 7: Successful sign-in response contains all required fields

*For any* valid registered user, a successful `POST /api/auth/signin` response must contain a non-null `token`, `type` equal to `"Bearer"`, a non-null `id`, the correct `username`, the correct `email`, and a non-empty `roles` list.

**Validates: Requirements 2.1**

---

## Error Handling

| Scenario | HTTP Status | Response body |
|---|---|---|
| Username already taken | 400 | `{"message": "Error: Username is already taken!"}` |
| Email already in use | 400 | `{"message": "Error: Email is already in use!"}` |
| Invalid credentials (bad password / unknown user) | 401 | Spring Security default via `AuthEntryPointJwt` |
| Missing / malformed JWT on protected endpoint | 401 | JSON body from `AuthEntryPointJwt`: `{status, error, message, path}` |
| Expired JWT | 401 | Same as above |
| Bean validation failure (blank fields, bad email format) | 400 | Spring Boot default validation error response |
| Role not found in DB (missing seed) | 500 | `RuntimeException("Error: Role is not found.")` — prevented by init.sql seed |

`AuthEntryPointJwt` ensures that unauthenticated access to protected resources always returns a machine-readable JSON body rather than an HTML error page, which is important for the React UI's Axios error handling.

---

## Testing Strategy

### Unit Tests

Focus on the pure logic layer — no Spring context, no database.

- **JwtUtils** — generate a token for a known username, parse it back, assert subject matches; assert expiry is within expected range; assert `validateJwtToken` returns `false` for expired, tampered, and empty tokens.
- **UserDetailsImpl** — `build(User)` correctly maps roles to `GrantedAuthority` names; `equals` is id-based.
- **AuthController** (with mocked dependencies) — signup returns 400 on duplicate username; signup returns 400 on duplicate email; signin returns `JwtResponse` with correct fields on valid credentials.

### Property-Based Tests

Use **jqwik** (Java property-based testing library) with a minimum of **100 iterations** per property.

Each test is tagged with the corresponding design property for traceability:

- **Feature: user-auth-service, Property 1: JWT subject round-trip**
  Generate arbitrary non-blank usernames; for each, build a mock `Authentication`, call `generateJwtToken`, then `getUserNameFromJwtToken`; assert equality.

- **Feature: user-auth-service, Property 2: JWT expiry is bounded**
  Generate arbitrary expiration durations (1 ms – 30 days); assert `exp - iat` equals the configured duration within 1 second.

- **Feature: user-auth-service, Property 3: Expired tokens are rejected**
  Generate tokens with a negative expiration offset (already expired); assert `validateJwtToken` returns `false`.

- **Feature: user-auth-service, Property 4: Tampered tokens are rejected**
  Generate valid tokens; randomly mutate one character in the signature segment; assert `validateJwtToken` returns `false`.

- **Feature: user-auth-service, Property 5: Duplicate username registration is rejected**
  Generate arbitrary valid usernames; register once successfully; attempt a second registration with the same username but different email/password; assert HTTP 400 with the exact error message.

- **Feature: user-auth-service, Property 6: Duplicate email registration is rejected**
  Same pattern as Property 5 but varying the email field.

- **Feature: user-auth-service, Property 7: Successful sign-in response contains all required fields**
  Generate arbitrary valid user credentials; register then sign in; assert all `JwtResponse` fields are present and non-null.

### Integration Tests

- Full Spring context with an in-memory H2 database (MySQL-compatible mode) or Testcontainers MySQL.
- `POST /api/auth/signup` happy path → 200 + success message.
- `POST /api/auth/signup` duplicate username → 400.
- `POST /api/auth/signin` valid credentials → 200 + JWT.
- `POST /api/auth/signin` invalid credentials → 401.
- Protected endpoint without token → 401 JSON body.

### Smoke Tests

- Container starts successfully with all required environment variables set.
- `/api/auth/signup` is reachable (no 404/500 on startup).
- All three roles exist in the `roles` table after `init.sql` runs.
