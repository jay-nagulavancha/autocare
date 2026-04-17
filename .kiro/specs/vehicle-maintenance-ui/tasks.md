# Implementation Plan: Vehicle Maintenance UI

## Overview

Scaffold a brand-new React 18 + TypeScript + Vite SPA in `autocare/vehicle-maintenance-ui/`. Build incrementally: project scaffold → types → API clients + auth context → routing + protected route → pages (Login, Register, Dashboard, Work Orders, Scheduling) → Docker/nginx. Property-based tests (fast-check) and unit tests (Vitest + React Testing Library) are woven in alongside each feature.

## Tasks

- [x] 1. Scaffold project and configure tooling
  - Run `npm create vite@latest` with React + TypeScript template inside `autocare/vehicle-maintenance-ui/`
  - Install dependencies: `react-router-dom`, `axios`, `fast-check`, `vitest`, `@vitest/ui`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`
  - Configure `vite.config.ts`: set `server.port = 3000`, add Vitest config block (`environment: 'jsdom'`, `globals: true`, `setupFiles`)
  - Create `src/setupTests.ts` importing `@testing-library/jest-dom`
  - Create `.env.example` and `.env` with `VITE_AUTH_API_URL=http://localhost:8080` and `VITE_MAINTENANCE_API_URL=http://localhost:8081`
  - Add `.env` to `.gitignore`
  - _Requirements: 6.1, 6.3_

- [x] 2. Define TypeScript interfaces
  - Create `src/types/index.ts` with all interfaces from the design: `SignInRequest`, `SignInResponse`, `SignUpRequest`, `MessageResponse`, `Vehicle`, `Technician`, `Bay`, `WorkOrderStatus`, `PartLine`, `LaborLine`, `StatusHistoryEntry`, `WorkOrder`, `WorkOrderSummary`, `WorkOrderListParams`, `ScheduleStatus`, `ServiceSchedule`, `CreateScheduleRequest`, `Page<T>`, `DashboardSummary`
  - _Requirements: 1.1, 1.4, 2.1, 3.1, 3.2, 4.1, 4.2_

- [x] 3. Implement Axios clients and interceptors
  - Create `src/api/authClient.ts`: `axios.create({ baseURL: import.meta.env.VITE_AUTH_API_URL ?? 'http://localhost:8080' })`
  - Create `src/api/maintenanceClient.ts`: `axios.create(...)` + request interceptor (attach `Authorization: Bearer <token>` from `localStorage`) + response interceptor (on 401: `localStorage.removeItem('jwt')`, `window.location.href = '/login'`)
  - [ ]* 3.1 Write property test for Bearer token attachment (Property 2)
    - // Feature: ui, Property 2: maintenanceClient attaches Bearer token on every request
    - Use `fc.string({ minLength: 1 })` for token; mock Axios adapter; assert every dispatched request has `Authorization: Bearer <token>`
    - **Validates: Requirements 1.6, 5.3**
  - [ ]* 3.2 Write property test for 401 clears token and redirects (Property 3)
    - // Feature: ui, Property 3: HTTP 401 from Maintenance Service clears token and redirects
    - Use `fc.string({ minLength: 1 })` for token; mock 401 response; assert `localStorage` no longer contains token and `window.location.href === '/login'`
    - **Validates: Requirements 2.3, 3.4, 5.1**
  - _Requirements: 1.6, 2.3, 3.4, 5.1, 5.3_

- [x] 4. Implement AuthContext and useAuth hook
  - Create `src/context/AuthContext.tsx` with `AuthUser` and `AuthContextValue` interfaces
  - Implement `AuthProvider`: initialise `token` and `user` from `localStorage` on mount; `login(token, user)` writes to `localStorage` + updates state; `logout()` removes from `localStorage`, clears state, navigates to `/login`
  - Export `useAuth` convenience hook
  - [ ]* 4.1 Write property test for auth token storage round-trip (Property 1)
    - // Feature: ui, Property 1: Auth token storage round-trip
    - Use `fc.string({ minLength: 1 })` for token and `fc.record(...)` for user; call `login` then `logout`; assert token present after login and absent after logout
    - **Validates: Requirements 1.2, 1.5**
  - [ ]* 4.2 Write unit tests for AuthContext
    - Test `login` stores token and updates context; test `logout` clears token and redirects; test unauthenticated initial state
    - _Requirements: 1.2, 1.5_
  - _Requirements: 1.2, 1.5_

- [ ] 5. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement routing, ProtectedRoute, and App shell
  - Create `src/components/ProtectedRoute.tsx`: reads `token` from `useAuth`; if null → `<Navigate to="/login" replace />`; else → `<Outlet />`
  - Create `src/App.tsx` with `<Routes>` matching the design: `/login`, `/register`, `ProtectedRoute` wrapping `/`, `/work-orders`, `/work-orders/:id`, `/schedules`; catch-all `<Navigate to="/" replace />`
  - Update `src/main.tsx` to wrap `<App />` in `<AuthProvider>` and `<BrowserRouter>`
  - [ ]* 6.1 Write unit tests for ProtectedRoute
    - Test unauthenticated user is redirected to `/login`; test authenticated user sees `<Outlet />`
    - _Requirements: 1.2, 5.1_
  - _Requirements: 1.2, 5.1_

- [x] 7. Implement shared components
  - Create `src/components/NavBar.tsx`: links to `/`, `/work-orders`, `/schedules`; logout button calls `useAuth().logout()`
  - Create `src/components/StatusBadge.tsx`: maps `WorkOrderStatus` values to coloured badge variants
  - _Requirements: 1.5, 3.1_

- [x] 8. Implement Login and Register pages
  - Create `src/pages/LoginPage.tsx`: form with username + password fields; on submit calls `authClient.post('/api/auth/signin')`; on 200 calls `AuthContext.login(token, user)` and navigates to `/`; on 401 shows "Invalid username or password." inline
  - Create `src/pages/RegisterPage.tsx`: form with username, email, password fields; on submit calls `authClient.post('/api/auth/signup')`; on 400 shows field-level errors from response body; on success navigates to `/login`
  - [ ]* 8.1 Write unit tests for LoginPage
    - Test form renders username + password fields; test successful login navigates to dashboard; test 401 shows error message without status code
    - _Requirements: 1.1, 1.2, 1.3_
  - [ ]* 8.2 Write unit tests for RegisterPage
    - Test form renders username, email, password fields; test 400 shows field-level errors
    - _Requirements: 1.4_
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 9. Implement Dashboard page
  - Create `src/pages/DashboardPage.tsx`: fetches `GET /api/v1/work-orders?status=OPEN`, `GET /api/v1/schedules` (today), and `GET /api/v1/bays` via `maintenanceClient`; renders `OpenWorkOrdersSummary`, `TodaySchedulesSummary`, `BayAvailabilitySummary` sub-components; shows loading spinner while fetching; shows error message on failure
  - [ ]* 9.1 Write unit tests for DashboardPage
    - Test all three summary sections render; test loading spinner shown during fetch; test 401 triggers redirect (via interceptor)
    - _Requirements: 2.1, 2.2, 2.3_
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 10. Implement useWorkOrders and useWorkOrder hooks
  - Create `src/hooks/useWorkOrders.ts`: accepts `WorkOrderListParams`; calls `GET /api/v1/work-orders` with params as query string; returns `{ data, loading, error }`
  - Create `src/hooks/useWorkOrder.ts`: accepts `id: number`; calls `GET /api/v1/work-orders/{id}`; returns `{ data, loading, error }`
  - [ ]* 10.1 Write property test for work order filters forwarded as query params (Property 4)
    - // Feature: ui, Property 4: Work order filters are forwarded as query parameters
    - Use `fc.option(fc.constantFrom('OPEN','IN_PROGRESS','PENDING_PARTS','COMPLETED','INVOICED'))`, `fc.option(fc.integer())`, `fc.option(fc.integer())`; assert the Axios call includes exactly the provided params and omits absent ones
    - **Validates: Requirements 3.1**
  - _Requirements: 3.1, 3.2_

- [x] 11. Implement WorkOrderListPage
  - Create `src/pages/WorkOrderListPage.tsx`: filter controls (status dropdown, vehicle id input, technician id input) bound to `useState`; passes filters to `useWorkOrders`; renders `WorkOrderTable` with rows linking to `/work-orders/:id`; shows loading spinner and error states
  - _Requirements: 3.1_

- [x] 12. Implement WorkOrderDetailPage
  - Create `src/pages/WorkOrderDetailPage.tsx`: reads `:id` from route params; uses `useWorkOrder(id)`; renders `WorkOrderHeader`, `PartLinesTable`, `LaborLinesTable`, `CostSummary`, `StatusHistory`, and `StatusTransitionForm`
  - `StatusTransitionForm`: on submit calls `PATCH /api/v1/work-orders/{id}/status`; on 200 refreshes work order in place (re-fetch or local state update); on 422 shows the `message` field from response body
  - [ ]* 12.1 Write property test for work order detail renders all fields (Property 5)
    - // Feature: ui, Property 5: Work order detail renders all fields
    - Use `fc.array(partLineArb)`, `fc.array(laborLineArb)`, `fc.array(historyArb)` to generate arbitrary work orders; assert every part name, labor description, total cost, and status history entry appears in the rendered output
    - **Validates: Requirements 3.2**
  - [ ]* 12.2 Write unit tests for StatusTransitionForm
    - Test successful PATCH updates displayed status without full reload; test 422 shows error message
    - _Requirements: 3.3_
  - _Requirements: 3.2, 3.3, 3.4_

- [ ] 13. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement useSchedules hook and SchedulingPage
  - Create `src/hooks/useSchedules.ts`: calls `GET /api/v1/schedules`; returns `{ data, loading, error, refresh }`
  - Create `src/pages/SchedulingPage.tsx`: renders `ScheduleForm` (vehicle, scheduledAt, serviceType, optional bay) and `ScheduleList`; `ScheduleForm` on submit calls `POST /api/v1/schedules`; on 201 calls `refresh()`; on 409 displays the `message` field from response body verbatim
  - [ ]* 14.1 Write property test for schedule list renders all returned schedules (Property 6)
    - // Feature: ui, Property 6: Schedule list renders all returned schedules
    - Use `fc.array(scheduleArb, { minLength: 1 })`; assert every schedule id or scheduledAt value appears in the rendered output
    - **Validates: Requirements 4.2**
  - [ ]* 14.2 Write property test for bay conflict error message displayed verbatim (Property 7)
    - // Feature: ui, Property 7: Bay conflict error message is displayed verbatim
    - Use `fc.string({ minLength: 1 })` for error message; mock 409 response with that message; assert the exact string appears in the rendered UI
    - **Validates: Requirements 4.4**
  - [ ]* 14.3 Write unit tests for SchedulingPage
    - Test successful schedule creation refreshes list; test 409 shows error message; test form fields render correctly
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 15. Implement JWT-not-rendered property test (Property 8)
  - [ ]* 15.1 Write property test for JWT value never rendered in UI (Property 8)
    - // Feature: ui, Property 8: JWT value is never rendered in the UI
    - Use `fc.string({ minLength: 10 })` for token; render each page with that token in `localStorage`; assert the token string does not appear as visible text in any DOM element
    - **Validates: Requirements 5.2**

- [x] 16. Add Dockerfile and nginx configuration
  - Create `autocare/vehicle-maintenance-ui/nginx.conf`: `listen 3000`, `root /usr/share/nginx/html`, `try_files $uri $uri/ /index.html`
  - Create `autocare/vehicle-maintenance-ui/Dockerfile`: multi-stage — `node:20-alpine` builder stage (`npm ci`, `npm run build` with `ARG VITE_AUTH_API_URL` and `ARG VITE_MAINTENANCE_API_URL`) → `nginx:alpine` serve stage (copy `dist/` to `/usr/share/nginx/html`, copy `nginx.conf`); `EXPOSE 3000`
  - _Requirements: 6.2, 6.3, 6.4_

- [ ] 17. Final checkpoint — Ensure all tests pass
  - Run `vitest --run` and confirm all tests pass. Ask the user if any questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each property test must use `numRuns: 100` in the fast-check config
- Property tests are tagged with `// Feature: ui, Property N: <text>` comments
- `vitest --run` executes tests in single-pass mode (no watch)
- The `VITE_*` env vars are baked into the static bundle at Docker build time — pass them as `ARG` in the Dockerfile and as `build.args` in `docker-compose.yml`
