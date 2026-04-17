# Product Overview — Vehicle Maintenance System

## Purpose

A multi-service vehicle maintenance management system for auto shops. It allows staff to register vehicles, manage work orders through a defined lifecycle, schedule future maintenance, assign technicians and bays, and track parts and labor costs. Customers can view their own vehicles and work order history.

## Three Independent Projects

| Project | Role | Port |
|---|---|---|
| `user-auth-service` | Identity and access — registration, login, JWT issuance, role management | 8080 |
| `vehicle-maintenance-service` | Business domain — vehicles, work orders, scheduling, technicians, bays, parts/labor | 8081 |
| `vehicle-maintenance-ui` | Frontend — login/register, dashboard, work orders, scheduling screens | 3000 |

## Locked Technology Choices

- **API style:** REST (JSON) everywhere — no GraphQL, no gRPC
- **Database:** MySQL — each service owns its own schema, no shared tables
- **Auth backend:** Spring Boot 3.x, Spring Security, jjwt, Spring Data JPA
- **Domain backend:** Spring Boot 3.x, Spring Web, Spring Validation, Spring Data JPA, Spring Security (JWT resource server only), springdoc-openapi
- **Frontend:** React with TypeScript, Vite, React Router, Axios
- **Containerisation:** Docker + Docker Compose for local full-stack development

## Monorepo Structure

```
/ (repo root)
├── user-auth-service/           ← standalone Maven project (existing Spring Boot app)
├── vehicle-maintenance-service/    ← standalone Maven project (new)
├── ui/                     ← standalone Node/React project (new)
├── db/
│   ├── user-auth-service/
│   │   └── init.sql        ← idempotent schema + role seed for auth DB
│   └── vehicle-maintenance-service/
│       └── init.sql        ← idempotent schema + seed data for domain DB
└── docker-compose.yml      ← orchestrates all four services for local dev
```

## User Roles

| Role | Access |
|---|---|
| `ROLE_ADMIN` | Full access to all operations |
| `ROLE_TECHNICIAN` | Access to assigned work orders and vehicle data |
| `ROLE_CUSTOMER` | Read-only access to own vehicles and work orders |

## Work Order Status State Machine

```
OPEN → IN_PROGRESS → PENDING_PARTS → IN_PROGRESS → COMPLETED → INVOICED
```

Transitions that skip states are rejected with HTTP 422.
