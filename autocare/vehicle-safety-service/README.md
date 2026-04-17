# vehicle-safety-service

**Platform:** Autocare  
**Group:** com.autocare  
**Status:** Planned

## Purpose

Manages safety incidents, recall tracking, inspection records, and safety compliance reporting per vehicle (VIN-based).

## Planned Stack

- Spring Boot 3.x, Spring Web, Spring Data JPA, MySQL
- REST API under `/api/v1/safety`
- JWT resource server (validates tokens from user-auth-service)
- Port: TBD

## Dependencies

- `user-auth-service` — JWT validation (shared JWT_SECRET)
- `vehicle-maintenance-service` — vehicle records referenced by VIN
