# vehicle-parts-service

**Platform:** Autocare  
**Group:** com.autocare  
**Status:** Planned

## Purpose

Manages vehicle parts inventory, part lookups by VIN/make/model, supplier catalog, and parts ordering for work orders in the vehicle-maintenance-service.

## Planned Stack

- Spring Boot 3.x, Spring Web, Spring Data JPA, MySQL
- REST API under `/api/v1/parts`
- JWT resource server (validates tokens from user-auth-service)
- Port: TBD

## Dependencies

- `user-auth-service` — JWT validation (shared JWT_SECRET)
- `vehicle-maintenance-service` — parts linked to work order part lines
