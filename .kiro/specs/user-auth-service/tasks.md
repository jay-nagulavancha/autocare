# Implementation Plan: User Auth Service

## Overview

Migrate the existing `com.bezkoder.springjwt` Spring Boot app into `autocare/user-auth-service/` as a standalone Maven project under `com.autocare.auth`. Update the role model, wire all configuration through environment variables, add a multi-stage Dockerfile, write the DB bootstrap script, and cover the JWT and registration logic with jqwik property-based tests.

## Tasks

- [x] 1. Scaffold the standalone Maven project at `autocare/user-auth-service/`
  - Create `autocare/user-auth-service/pom.xml` with `groupId=com.autocare`, `artifactId=user-auth-service`, Spring Boot 3.1.x parent, and the same dependencies as the root `pom.xml` (spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation, spring-boot-starter-web, mysql-connector-j, jjwt-api/impl/jackson 0.11.5, spring-boot-starter-test, spring-security-test) plus jqwik for property-based testing
  - Copy `mvnw`, `mvnw.cmd`, and `.mvn/` from the repo root into `autocare/user-auth-service/`
  - Create the directory tree `autocare/user-auth-service/src/main/java/com/autocare/auth/` and `autocare/user-auth-service/src/test/java/com/autocare/auth/`
  - Create `autocare/user-auth-service/src/main/resources/application.properties` using the environment-variable-backed template from the design (DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET, JWT_EXPIRATION_MS — no hard-coded secrets)
  - _Requirements: 4.1, 4.3_

- [x] 2. Migrate and refactor source files into `com.autocare.auth`
  - [x] 2.1 Copy all Java source files from `src/main/java/com/bezkoder/springjwt/` into the matching sub-packages under `com.autocare.auth`, updating every `package` and `import` statement
    - Entry point: `AutocareAuthApplication.java` (rename class from `SpringBootSecurityJwtApplication`)
    - Preserve all existing classes: `AuthController`, `ERole`, `Role`, `User`, `LoginRequest`, `SignupRequest`, `JwtResponse`, `MessageResponse`, `UserRepository`, `RoleRepository`, `WebSecurityConfig`, `AuthEntryPointJwt`, `AuthTokenFilter`, `JwtUtils`, `UserDetailsImpl`, `UserDetailsServiceImpl`
    - _Requirements: 4.1_

  - [x] 2.2 Update `ERole` enum to `ROLE_ADMIN`, `ROLE_TECHNICIAN`, `ROLE_CUSTOMER` (remove `ROLE_USER`, `ROLE_MODERATOR`)
    - File: `com/autocare/auth/models/ERole.java`
    - _Requirements: 3.1_

  - [x] 2.3 Update `AuthController.registerUser()` role-resolution switch to map `"admin"` → `ROLE_ADMIN`, `"technician"` → `ROLE_TECHNICIAN`, `"customer"` → `ROLE_CUSTOMER`; default (null or unrecognised) → `ROLE_CUSTOMER`
    - Remove all references to `ROLE_USER` and `ROLE_MODERATOR`
    - File: `com/autocare/auth/controllers/AuthController.java`
    - _Requirements: 1.1, 3.1_

  - [x] 2.4 Update `JwtUtils` `@Value` property keys from `bezkoder.app.*` to `autocare.app.jwtSecret` and `autocare.app.jwtExpirationMs`; update `application.properties` keys to match
    - File: `com/autocare/auth/security/jwt/JwtUtils.java`
    - _Requirements: 2.3, 2.4_

- [x] 3. Write the DB bootstrap script
  - Create `autocare/db/user-auth-service/init.sql` with `CREATE TABLE IF NOT EXISTS` for `roles`, `users`, and `user_roles` exactly as specified in the design, followed by `INSERT IGNORE INTO roles` for `ROLE_ADMIN`, `ROLE_TECHNICIAN`, `ROLE_CUSTOMER`
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 4. Write the Dockerfile
  - Create `autocare/user-auth-service/Dockerfile` as a two-stage build: stage 1 uses `eclipse-temurin:17-jdk-alpine`, copies `.mvn/`, `mvnw`, `pom.xml`, runs `./mvnw dependency:go-offline -B`, copies `src/`, runs `./mvnw clean package -DskipTests -B`; stage 2 uses `eclipse-temurin:17-jre-alpine`, copies the JAR, `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "app.jar"]`
  - _Requirements: 4.2, 4.3, 4.4_

- [ ] 5. Checkpoint — verify the project builds
  - Run `./mvnw clean package -DskipTests -B` inside `autocare/user-auth-service/` and confirm it produces a JAR in `target/`
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement unit tests for `JwtUtils`
  - [x] 6.1 Create `JwtUtilsTest` in `com/autocare/auth/security/jwt/`
    - Instantiate `JwtUtils` directly (no Spring context); inject a test Base64 secret and expiry via reflection or a constructor
    - Test: `generateJwtToken` → `getUserNameFromJwtToken` returns the same username (example-based)
    - Test: `validateJwtToken` returns `false` for an empty string, a random string, and a structurally valid but wrong-key token
    - _Requirements: 2.3_

  - [ ]* 6.2 Write property test — Property 1: JWT subject round-trip
    - Use `@Property(tries = 100)` from jqwik; generate arbitrary non-blank usernames with `@ForAll @NotBlank String username`
    - Build a mock `Authentication` backed by a `UserDetailsImpl` with that username; call `generateJwtToken`; assert `getUserNameFromJwtToken` returns the same value
    - **Property 1: JWT subject round-trip**
    - **Validates: Requirements 2.1, 2.3**

  - [ ]* 6.3 Write property test — Property 2: JWT expiry is bounded
    - Generate arbitrary expiry durations between 1 ms and 30 days; configure `JwtUtils` with each duration; generate a token; parse the `exp` and `iat` claims; assert `exp - iat` equals the configured duration within 1 000 ms
    - **Property 2: JWT expiry is bounded**
    - **Validates: Requirements 2.3**

  - [ ]* 6.4 Write property test — Property 3: Expired tokens are rejected
    - Generate tokens with `jwtExpirationMs = -1000` (already expired); assert `validateJwtToken` returns `false`
    - **Property 3: Expired tokens are rejected**
    - **Validates: Requirements 2.3**

  - [ ]* 6.5 Write property test — Property 4: Tampered tokens are rejected
    - Generate a valid token; split on `.`; randomly replace one character in the signature segment using jqwik `@ForAll` index and character arbitraries; reassemble; assert `validateJwtToken` returns `false`
    - **Property 4: Tampered tokens are rejected**
    - **Validates: Requirements 2.3, 2.4**

- [x] 7. Implement unit tests for `UserDetailsImpl`
  - [x] 7.1 Create `UserDetailsImplTest` in `com/autocare/auth/security/services/`
    - Test `build(User)` maps `ROLE_ADMIN`, `ROLE_TECHNICIAN`, `ROLE_CUSTOMER` roles to the correct `GrantedAuthority` names
    - Test `equals` is id-based (two instances with same id but different usernames are equal)
    - _Requirements: 1.1_

- [x] 8. Implement unit tests for `AuthController`
  - [x] 8.1 Create `AuthControllerTest` using `@WebMvcTest(AuthController.class)` with mocked `UserRepository`, `RoleRepository`, `PasswordEncoder`, `AuthenticationManager`, and `JwtUtils`
    - Test `POST /api/auth/signup` with duplicate username → HTTP 400, body contains `"Error: Username is already taken!"`
    - Test `POST /api/auth/signup` with duplicate email → HTTP 400, body contains `"Error: Email is already in use!"`
    - Test `POST /api/auth/signup` happy path → HTTP 200, body contains `"User registered successfully!"`
    - Test `POST /api/auth/signin` valid credentials → HTTP 200, response body contains `token`, `type`, `id`, `username`, `email`, `roles`
    - _Requirements: 1.1, 1.2, 1.3, 2.1_

  - [ ]* 8.2 Write property test — Property 5: Duplicate username registration is rejected
    - Generate arbitrary valid usernames; stub `userRepository.existsByUsername()` to return `true`; assert HTTP 400 with exact message `"Error: Username is already taken!"`
    - **Property 5: Duplicate username registration is rejected**
    - **Validates: Requirements 1.2**

  - [ ]* 8.3 Write property test — Property 6: Duplicate email registration is rejected
    - Generate arbitrary valid email strings; stub `userRepository.existsByEmail()` to return `true`; assert HTTP 400 with exact message `"Error: Email is already in use!"`
    - **Property 6: Duplicate email registration is rejected**
    - **Validates: Requirements 1.3**

  - [ ]* 8.4 Write property test — Property 7: Successful sign-in response contains all required fields
    - Generate arbitrary valid username/email pairs; stub `AuthenticationManager` and `JwtUtils`; call `POST /api/auth/signin`; assert response contains non-null `token`, `type == "Bearer"`, non-null `id`, correct `username`, correct `email`, non-empty `roles`
    - **Property 7: Successful sign-in response contains all required fields**
    - **Validates: Requirements 2.1**

- [ ] 9. Checkpoint — ensure all tests pass
  - Run `./mvnw test` inside `autocare/user-auth-service/` and confirm zero failures
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- All property tests use jqwik `@Property(tries = 100)` minimum
- `JWT_SECRET` has no default — the app will fail to start if absent (fail-fast by design)
- The root `pom.xml` and `src/` tree are the migration source; do not delete them until the new service is verified
- `autocare/db/user-auth-service/init.sql` is mounted by Docker Compose and run on every container start — idempotency is mandatory
