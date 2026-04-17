# Requirements Document — Auth Service

## Introduction

The Auth Service is an existing Spring Boot 3.x application responsible for identity and access management. It handles user registration, login, JWT issuance, and role management, backed by a dedicated MySQL schema. No other service shares its database tables.

**Stack:** Spring Boot 3.x, Spring Security, JWT (jjwt), Spring Data JPA, MySQL.  
**API style:** REST.  
**Port:** 8080.

---

## Glossary

- **Auth_Service**: This application — responsible for identity and access management.
- **JWT**: JSON Web Token — a signed, stateless bearer token issued by this service and validated by downstream services.
- **JWT_Secret**: A shared symmetric key (HS256) configured via environment variable; never hard-coded in source.
- **ROLE_ADMIN**: A role granting full access to all system operations.
- **ROLE_TECHNICIAN**: A role granting access to assigned work orders and vehicle data.
- **ROLE_CUSTOMER**: A role granting read-only access to a customer's own vehicles and work orders.
- **Bearer_Token**: An HTTP Authorization header value of the form `Authorization: Bearer <JWT>`.

---

## Requirements

### Requirement 1: User Registration

**User Story:** As a new user, I want to register an account so that I can access the system with an appropriate role.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/auth/signup` with a valid username, email, password, and optional role list, THE Auth_Service SHALL create a new user account and return HTTP 200 with a success message.
2. WHEN a POST request is made to `/api/auth/signup` with a username that already exists, THE Auth_Service SHALL return HTTP 400 with the message "Error: Username is already taken!".
3. WHEN a POST request is made to `/api/auth/signup` with an email that already exists, THE Auth_Service SHALL return HTTP 400 with the message "Error: Email is already in use!".
4. THE Auth_Service SHALL store user and role data exclusively in its own MySQL schema and SHALL NOT share tables with any other service.

---

### Requirement 2: User Authentication and JWT Issuance

**User Story:** As a registered user, I want to log in so that I receive a JWT I can use to access protected resources.

#### Acceptance Criteria

1. WHEN a POST request is made to `/api/auth/signin` with valid credentials, THE Auth_Service SHALL return HTTP 200 with a JWT, the user's id, username, email, and role list.
2. WHEN a POST request is made to `/api/auth/signin` with invalid credentials, THE Auth_Service SHALL return HTTP 401.
3. THE Auth_Service SHALL sign all issued JWTs using HS256 with the configured JWT_Secret and set an expiration of `bezkoder.app.jwtExpirationMs` milliseconds from issuance.
4. THE Auth_Service SHALL read the JWT_Secret from an environment variable and SHALL NOT hard-code it in any source file or configuration committed to source control.

---

### Requirement 3: Role Seeding

**User Story:** As a system operator, I want the three system roles to exist in the database on first start so that user registration works without manual setup.

#### Acceptance Criteria

1. THE Auth_Service database schema SHALL contain a `roles` table with rows for `ROLE_ADMIN`, `ROLE_TECHNICIAN`, and `ROLE_CUSTOMER`.
2. THE bootstrap SQL script (`db/user-auth-service/init.sql`) SHALL create the `users`, `roles`, and `user_roles` tables using `CREATE TABLE IF NOT EXISTS` so the script is safe to re-run.
3. THE bootstrap SQL script SHALL insert the three role rows using `INSERT IGNORE` (or equivalent idempotent syntax) so roles are always present after initialisation without duplicating data.

---

### Requirement 4: Build and Containerisation

**User Story:** As a developer, I want the Auth Service to be buildable and runnable in Docker so that it can be started as part of the full stack with a single command.

#### Acceptance Criteria

1. THE Auth_Service directory SHALL contain a Maven wrapper (`mvnw` / `mvnw.cmd`) so the service can be built with `./mvnw clean package -DskipTests` without a globally installed Maven binary.
2. THE Auth_Service SHALL have a `Dockerfile` that builds the JAR and produces a runnable image.
3. THE Auth_Service container SHALL accept database credentials (host, port, database name, username, password) and the JWT_Secret via environment variables.
4. THE Auth_Service container SHALL expose port 8080.
5. THE Auth_Service container SHALL depend on the `mysql` service being healthy before starting (enforced via Docker Compose `depends_on: condition: service_healthy`).
