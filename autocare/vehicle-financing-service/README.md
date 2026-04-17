# vehicle-financing-service

**Platform:** Autocare  
**Group:** com.autocare  
**Status:** Planned

## Purpose

Manages vehicle financing applications, loan/lease tracking, payment schedules, and financing status per vehicle or customer account.

## Planned Stack

- Spring Boot 3.x, Spring Web, Spring Data JPA, MySQL
- REST API under `/api/v1/financing`
- JWT resource server (validates tokens from user-auth-service)
- Port: TBD

## Dependencies

- `user-auth-service` — JWT validation (shared JWT_SECRET)
- `vehicle-maintenance-service` — vehicle records referenced by VIN
