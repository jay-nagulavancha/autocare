# Requirements Document — UI

## Introduction

The UI is a new frontend application that provides screens for login/register, dashboards, work orders, and scheduling. It communicates with the Auth Service for authentication and the Maintenance Service for all business operations, passing the JWT as a Bearer token on every request.

**Stack:** React (with TypeScript), Vite, React Router, Axios (or fetch).  
**Port:** 3000 (development and Docker).  
**No backend logic** — browser only; no server-side rendering required for v1.

---

## Glossary

- **Auth_Service**: The Spring Boot authentication backend at `http://localhost:8080` (or `http://user-auth-service:8080` in Docker).
- **Maintenance_Service**: The Spring Boot domain backend at `http://localhost:8081` (or `http://vehicle-maintenance-service:8081` in Docker).
- **JWT**: JSON Web Token stored in browser storage after login; attached as a Bearer token on every Maintenance_Service request.
- **Bearer_Token**: An HTTP Authorization header value of the form `Authorization: Bearer <JWT>`.
- **Work_Order**: A maintenance job record returned by the Maintenance_Service.
- **Service_Schedule**: A planned future maintenance event returned by the Maintenance_Service.

---

## Requirements

### Requirement 1: Authentication Screens

**User Story:** As an end user, I want login and registration screens so that I can access the system from a browser.

#### Acceptance Criteria

1. THE UI SHALL provide a login screen that collects username and password and submits them to `POST /api/auth/signin` on the Auth_Service.
2. WHEN the Auth_Service returns a successful sign-in response, THE UI SHALL store the JWT in browser storage and redirect the user to the main dashboard.
3. WHEN the Auth_Service returns HTTP 401 on sign-in, THE UI SHALL display a user-friendly error message without exposing the raw HTTP status code.
4. THE UI SHALL provide a registration screen that collects username, email, and password and submits them to `POST /api/auth/signup` on the Auth_Service.
5. WHEN the user logs out, THE UI SHALL remove the JWT from browser storage and redirect to the login screen.
6. WHILE the JWT is present in browser storage, THE UI SHALL attach it as a Bearer_Token on every request to the Maintenance_Service.

---

### Requirement 2: Dashboard Screen

**User Story:** As a service advisor or technician, I want a dashboard so that I can see the shop's current status at a glance.

#### Acceptance Criteria

1. THE UI SHALL provide a dashboard screen that displays a summary of open Work_Orders, today's Service_Schedules, and bay availability.
2. THE dashboard SHALL fetch its data from the Maintenance_Service using the stored JWT.
3. IF the Maintenance_Service returns HTTP 401 on any dashboard request, THE UI SHALL redirect the user to the login screen and clear the stored JWT.

---

### Requirement 3: Work Order Screens

**User Story:** As a service advisor or technician, I want work order list and detail screens so that I can manage maintenance jobs from the browser.

#### Acceptance Criteria

1. THE UI SHALL provide a work order list screen that supports filtering by status, vehicle, and technician.
2. THE UI SHALL provide a work order detail screen that displays all Work_Order fields, Part_Lines, Labor_Lines, total cost, and status history.
3. WHEN the user submits a status transition on the work order detail screen, THE UI SHALL call `PATCH /api/v1/work-orders/{id}/status` on the Maintenance_Service and refresh the displayed status without a full page reload.
4. IF the Maintenance_Service returns HTTP 401 on any work order request, THE UI SHALL redirect the user to the login screen and clear the stored JWT.

---

### Requirement 4: Scheduling Screen

**User Story:** As a service advisor, I want a scheduling screen so that I can create and view Service_Schedules.

#### Acceptance Criteria

1. THE UI SHALL provide a scheduling screen that allows creating new Service_Schedules by selecting a vehicle, date-time, service type, and optional bay.
2. THE scheduling screen SHALL display existing Service_Schedules in a calendar or list view.
3. WHEN a schedule is successfully created, THE UI SHALL refresh the schedule list without a full page reload.
4. IF the Maintenance_Service returns HTTP 409 on schedule creation (bay conflict), THE UI SHALL display the error message returned by the service.

---

### Requirement 5: Session and Token Handling

**User Story:** As a user, I want the UI to handle expired sessions gracefully so that I am never stuck on a broken screen.

#### Acceptance Criteria

1. IF the Maintenance_Service returns HTTP 401 on any request, THE UI SHALL clear the stored JWT and redirect the user to the login screen.
2. THE UI SHALL not expose the raw JWT value in any rendered UI element.
3. THE UI SHALL use an HTTP client interceptor (e.g., Axios interceptor) to attach the Bearer_Token header automatically on all Maintenance_Service requests, rather than setting it manually in each call.

---

### Requirement 6: Build and Containerisation

**User Story:** As a developer, I want the UI to be buildable and runnable in Docker so it can be started as part of the full stack.

#### Acceptance Criteria

1. THE UI directory SHALL contain a `package.json` with a `build` script so the UI can be built with `npm run build` (or `yarn build`).
2. THE UI SHALL have a `Dockerfile` that builds the static assets and serves them (e.g., via nginx or a Node static server).
3. THE UI container SHALL accept the Auth_Service and Maintenance_Service base URLs via environment variables at build time or runtime.
4. THE UI container SHALL expose port 3000.
5. WHEN `docker compose up` is run at the monorepo root, THE UI container SHALL start after both backend services are available.
