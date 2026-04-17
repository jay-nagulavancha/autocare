# Design Document — UI

## Overview

The UI is a single-page application (SPA) built with React 18, TypeScript, Vite, and React Router v6. It runs on port 3000 in both development and Docker environments. The application has no server-side rendering — all logic runs in the browser.

The UI communicates with two backends:
- **Auth Service** (port 8080) — login and registration only, no JWT required
- **Maintenance Service** (port 8081) — all business operations, JWT required on every request

JWT tokens are stored in `localStorage` after a successful sign-in. An Axios interceptor on the `maintenanceClient` instance automatically attaches `Authorization: Bearer <token>` to every outgoing request. Any HTTP 401 response from the Maintenance Service triggers an automatic logout: the token is cleared and the user is redirected to `/login`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Browser (port 3000)                   │
│                                                          │
│  React Router v6                                         │
│  ├── /login          → LoginPage                         │
│  ├── /register       → RegisterPage                      │
│  └── (ProtectedRoute)                                    │
│      ├── /           → DashboardPage                     │
│      ├── /work-orders → WorkOrderListPage                │
│      ├── /work-orders/:id → WorkOrderDetailPage          │
│      └── /schedules  → SchedulingPage                    │
│                                                          │
│  AuthContext (React Context)                             │
│  ├── token: string | null  (from localStorage)           │
│  ├── user: AuthUser | null                               │
│  ├── login(token, user)                                  │
│  └── logout()                                            │
│                                                          │
│  Axios Clients                                           │
│  ├── authClient      → VITE_AUTH_API_URL (:8080)         │
│  └── maintenanceClient → VITE_MAINTENANCE_API_URL (:8081)│
│      └── interceptor: attach Bearer token                │
│      └── interceptor: on 401 → logout + redirect         │
└─────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
  Auth Service :8080          Maintenance Service :8081
  POST /api/auth/signin       GET/POST/PATCH /api/v1/**
  POST /api/auth/signup
```

### Key Design Decisions

- **React Context for auth state** — the auth state (token + user) is small and read-only after login. A full state management library (Redux, Zustand) is unnecessary overhead for this scope.
- **Two Axios instances** — `authClient` and `maintenanceClient` are separate to avoid accidentally attaching the JWT to auth endpoints, and to keep the interceptor logic scoped to the maintenance client only.
- **localStorage for JWT** — consistent with the locked decision. The token is never rendered in any UI element.
- **ProtectedRoute component** — wraps all authenticated routes; redirects to `/login` if no token is present in context.
- **Vite environment variables** — `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` are injected at build time, making the same Docker image usable across environments by rebuilding with different env vars.

---

## Components and Interfaces

### Project Structure

```
ui/
├── public/
├── src/
│   ├── main.tsx                  ← Vite entry point
│   ├── App.tsx                   ← Router setup
│   ├── api/
│   │   ├── authClient.ts         ← Axios instance for Auth Service
│   │   └── maintenanceClient.ts  ← Axios instance + interceptor for Maintenance Service
│   ├── context/
│   │   └── AuthContext.tsx       ← AuthContext + AuthProvider + useAuth hook
│   ├── components/
│   │   ├── ProtectedRoute.tsx    ← Redirects to /login if unauthenticated
│   │   ├── NavBar.tsx            ← Top navigation with logout button
│   │   └── StatusBadge.tsx       ← Coloured badge for work order status
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── WorkOrderListPage.tsx
│   │   ├── WorkOrderDetailPage.tsx
│   │   └── SchedulingPage.tsx
│   ├── types/
│   │   └── index.ts              ← All TypeScript interfaces for API shapes
│   └── hooks/
│       ├── useWorkOrders.ts      ← Data-fetching hook for work order list
│       ├── useWorkOrder.ts       ← Data-fetching hook for single work order
│       └── useSchedules.ts      ← Data-fetching hook for schedules
├── .env                          ← Local dev defaults (gitignored)
├── .env.example                  ← Committed template
├── Dockerfile
├── nginx.conf
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### Component Hierarchy

```
App
└── AuthProvider
    └── Router
        ├── /login          → LoginPage
        │   └── LoginForm
        ├── /register       → RegisterPage
        │   └── RegisterForm
        └── ProtectedRoute
            └── NavBar + <Outlet>
                ├── /               → DashboardPage
                │   ├── OpenWorkOrdersSummary
                │   ├── TodaySchedulesSummary
                │   └── BayAvailabilitySummary
                ├── /work-orders    → WorkOrderListPage
                │   ├── WorkOrderFilters (status, vehicle, technician)
                │   └── WorkOrderTable
                ├── /work-orders/:id → WorkOrderDetailPage
                │   ├── WorkOrderHeader
                │   ├── PartLinesTable
                │   ├── LaborLinesTable
                │   ├── CostSummary
                │   ├── StatusHistory
                │   └── StatusTransitionForm
                └── /schedules      → SchedulingPage
                    ├── ScheduleForm (create new)
                    └── ScheduleList (calendar or list toggle)
```

### Axios Client Configuration

**`src/api/authClient.ts`**
```typescript
import axios from 'axios';

export const authClient = axios.create({
  baseURL: import.meta.env.VITE_AUTH_API_URL ?? 'http://localhost:8080',
});
```

**`src/api/maintenanceClient.ts`**
```typescript
import axios from 'axios';

export const maintenanceClient = axios.create({
  baseURL: import.meta.env.VITE_MAINTENANCE_API_URL ?? 'http://localhost:8081',
});

// Request interceptor — attach JWT
maintenanceClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401
maintenanceClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('jwt');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

### AuthContext

```typescript
// src/context/AuthContext.tsx
interface AuthUser {
  id: number;
  username: string;
  email: string;
  roles: string[];
}

interface AuthContextValue {
  token: string | null;
  user: AuthUser | null;
  login: (token: string, user: AuthUser) => void;
  logout: () => void;
  isAuthenticated: boolean;
}
```

`login()` writes the token to `localStorage` and updates context state.  
`logout()` removes the token from `localStorage`, clears context state, and navigates to `/login`.

### ProtectedRoute

```typescript
// src/components/ProtectedRoute.tsx
// Reads token from AuthContext.
// If null → <Navigate to="/login" replace />
// If present → <Outlet />
```

---

## Data Models

All TypeScript interfaces live in `src/types/index.ts` and mirror the API response shapes exactly.

```typescript
// ── Auth ──────────────────────────────────────────────────────────────────

export interface SignInRequest {
  username: string;
  password: string;
}

export interface SignInResponse {
  token: string;
  type: string;       // "Bearer"
  id: number;
  username: string;
  email: string;
  roles: string[];    // e.g. ["ROLE_ADMIN"]
}

export interface SignUpRequest {
  username: string;
  email: string;
  password: string;
  role?: string[];    // e.g. ["admin"]
}

export interface MessageResponse {
  message: string;
}

// ── Vehicles ──────────────────────────────────────────────────────────────

export interface Vehicle {
  id: number;
  vin: string;
  make: string;
  model: string;
  year: number;
  ownerUsername: string;
}

// ── Technicians ───────────────────────────────────────────────────────────

export interface Technician {
  id: number;
  name: string;
  active: boolean;
  workOrderCount?: number;
}

// ── Bays ──────────────────────────────────────────────────────────────────

export interface Bay {
  id: number;
  name: string;
  active: boolean;
  available?: boolean;
}

// ── Work Orders ───────────────────────────────────────────────────────────

export type WorkOrderStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'PENDING_PARTS'
  | 'COMPLETED'
  | 'INVOICED';

export interface PartLine {
  id: number;
  partName: string;
  quantity: number;
  unitCost: number;
}

export interface LaborLine {
  id: number;
  description: string;
  hours: number;
  rate: number;
}

export interface StatusHistoryEntry {
  id: number;
  previousStatus: WorkOrderStatus | null;
  newStatus: WorkOrderStatus;
  changedBy: string;
  changedAt: string; // ISO 8601
}

export interface WorkOrder {
  id: number;
  vehicle: Vehicle;
  technician: Technician | null;
  bay: Bay | null;
  status: WorkOrderStatus;
  description: string;
  createdAt: string; // ISO 8601
  partLines: PartLine[];
  laborLines: LaborLine[];
  totalCost: number;
  statusHistory: StatusHistoryEntry[];
}

export interface WorkOrderSummary {
  id: number;
  vehicleVin: string;
  status: WorkOrderStatus;
  technicianName: string | null;
  createdAt: string;
}

export interface WorkOrderListParams {
  status?: WorkOrderStatus;
  vehicleId?: number;
  technicianId?: number;
  page?: number;
  size?: number;
}

// ── Schedules ─────────────────────────────────────────────────────────────

export type ScheduleStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface ServiceSchedule {
  id: number;
  vehicle: Vehicle;
  bay: Bay | null;
  scheduledAt: string; // ISO 8601 UTC
  serviceType: string;
  status: ScheduleStatus;
}

export interface CreateScheduleRequest {
  vehicleId: number;
  scheduledAt: string; // ISO 8601 UTC
  serviceType: string;
  bayId?: number;
}

// ── Pagination ────────────────────────────────────────────────────────────

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (0-indexed)
  size: number;
}

// ── Dashboard ─────────────────────────────────────────────────────────────

export interface DashboardSummary {
  openWorkOrderCount: number;
  todaySchedules: ServiceSchedule[];
  bays: Bay[];
}
```

---

## Routing (React Router v6)

```typescript
// src/App.tsx
<Routes>
  <Route path="/login"    element={<LoginPage />} />
  <Route path="/register" element={<RegisterPage />} />
  <Route element={<ProtectedRoute />}>
    <Route path="/"                  element={<DashboardPage />} />
    <Route path="/work-orders"       element={<WorkOrderListPage />} />
    <Route path="/work-orders/:id"   element={<WorkOrderDetailPage />} />
    <Route path="/schedules"         element={<SchedulingPage />} />
  </Route>
  <Route path="*" element={<Navigate to="/" replace />} />
</Routes>
```

Unauthenticated users hitting any protected route are redirected to `/login`. After a successful login, the user is redirected to `/` (dashboard).

---

## State Management

React Context is used exclusively for auth state (`AuthContext`). No global state library is introduced.

Page-level data (work orders, schedules, dashboard data) is fetched inside custom hooks (`useWorkOrders`, `useWorkOrder`, `useSchedules`) using `useEffect` + `useState`. Each hook manages its own `loading`, `error`, and `data` state. This keeps data fetching co-located with the components that need it and avoids unnecessary global state.

Filter state on the work order list is managed with `useState` inside `WorkOrderListPage` and passed as query params to the hook.

---

## Auth Flow

```
1. User visits /work-orders (protected)
   └── ProtectedRoute checks AuthContext.token
       └── null → redirect to /login

2. User submits login form
   └── authClient.post('/api/auth/signin', { username, password })
       ├── 200 → AuthContext.login(token, user)
       │         localStorage.setItem('jwt', token)
       │         navigate('/')
       └── 401 → display "Invalid username or password"

3. Subsequent requests to Maintenance Service
   └── maintenanceClient interceptor reads localStorage.getItem('jwt')
       └── sets Authorization: Bearer <token>

4. Maintenance Service returns 401 (expired/invalid token)
   └── response interceptor:
       ├── localStorage.removeItem('jwt')
       └── window.location.href = '/login'

5. User clicks logout
   └── AuthContext.logout()
       ├── localStorage.removeItem('jwt')
       ├── set token/user to null in context
       └── navigate('/login')
```

---

## Dockerfile Design

Multi-stage build: Node for building static assets, nginx for serving.

```dockerfile
# Stage 1 — build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_AUTH_API_URL
ARG VITE_MAINTENANCE_API_URL
RUN npm run build

# Stage 2 — serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
```

**`nginx.conf`** — serves the SPA and forwards unknown paths to `index.html` (required for client-side routing):

```nginx
server {
    listen 3000;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

The `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` build args are passed from `docker-compose.yml` so the correct backend URLs are baked into the static bundle at build time.

---

## Environment Variable Configuration

| Variable | Purpose | Dev default |
|---|---|---|
| `VITE_AUTH_API_URL` | Base URL for Auth Service | `http://localhost:8080` |
| `VITE_MAINTENANCE_API_URL` | Base URL for Maintenance Service | `http://localhost:8081` |

**`.env.example`** (committed):
```
VITE_AUTH_API_URL=http://localhost:8080
VITE_MAINTENANCE_API_URL=http://localhost:8081
```

**`.env`** (gitignored, local dev):
```
VITE_AUTH_API_URL=http://localhost:8080
VITE_MAINTENANCE_API_URL=http://localhost:8081
```

In Docker Compose, the build args are set to the internal Docker network hostnames:
```yaml
build:
  args:
    VITE_AUTH_API_URL: http://user-auth-service:8080
    VITE_MAINTENANCE_API_URL: http://vehicle-maintenance-service:8081
```


---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Auth token storage round-trip

*For any* valid sign-in response containing a token string, calling `login(token, user)` must store that exact token in `localStorage`, and subsequently calling `logout()` must remove it so that `localStorage` no longer contains the token.

**Validates: Requirements 1.2, 1.5**

---

### Property 2: Maintenance client attaches Bearer token on every request

*For any* non-empty token string stored in `localStorage`, every request dispatched through `maintenanceClient` must include an `Authorization` header with the value `Bearer <token>`, regardless of the request method, path, or body.

**Validates: Requirements 1.6, 5.3**

---

### Property 3: HTTP 401 from Maintenance Service clears token and redirects

*For any* token string stored in `localStorage`, when `maintenanceClient` receives an HTTP 401 response on any request, the token must be removed from `localStorage` and the browser must be redirected to `/login`.

**Validates: Requirements 2.3, 3.4, 5.1**

---

### Property 4: Work order filters are forwarded as query parameters

*For any* combination of filter values (status, vehicleId, technicianId — each independently present or absent), the API call made by the work order list hook must include exactly those filter values as query parameters and omit parameters that were not specified.

**Validates: Requirements 3.1**

---

### Property 5: Work order detail renders all fields

*For any* work order object with arbitrary part lines, labor lines, status history entries, and a computed total cost, the work order detail page must render all of those fields — including every part name, every labor description, the total cost value, and every status history entry.

**Validates: Requirements 3.2**

---

### Property 6: Schedule list renders all returned schedules

*For any* non-empty list of service schedules returned by the API, every schedule in the list must appear in the rendered scheduling screen output (identified by schedule id or scheduled date-time).

**Validates: Requirements 4.2**

---

### Property 7: Bay conflict error message is displayed verbatim

*For any* error message string returned in an HTTP 409 response from the schedule creation endpoint, that exact message string must appear in the rendered UI after the failed submission.

**Validates: Requirements 4.4**

---

### Property 8: JWT value is never rendered in the UI

*For any* token string stored in `localStorage`, the rendered output of any page in the application must not contain that token string as visible text in any DOM element.

**Validates: Requirements 5.2**

---

## Error Handling

| Scenario | Handling |
|---|---|
| Auth Service 401 on login | Show inline error: "Invalid username or password." Do not expose status code. |
| Auth Service 400 on signup | Show field-level validation errors from response body. |
| Maintenance Service 401 (any request) | Interceptor clears JWT, redirects to `/login`. |
| Maintenance Service 403 | Show "You do not have permission to perform this action." |
| Maintenance Service 404 | Show "Resource not found." |
| Maintenance Service 409 on schedule create | Show the `message` field from the response body verbatim. |
| Maintenance Service 422 on status transition | Show the `message` field from the response body (invalid transition). |
| Network error / timeout | Show "Unable to reach the server. Please check your connection." |
| Loading states | Each page shows a loading spinner while data is being fetched. |

Error messages are displayed inline near the relevant form or section — never as raw HTTP status codes or stack traces.

---

## Testing Strategy

### Dual Testing Approach

Both unit/example-based tests and property-based tests are used:

- **Unit tests** — specific examples, form rendering, API call verification, error display
- **Property tests** — universal properties across generated inputs (Properties 1–8 above)

### Property-Based Testing

The feature involves pure functions and deterministic logic (token storage, interceptor attachment, filter forwarding, data rendering) that are well-suited to property-based testing.

**Library:** [fast-check](https://github.com/dubzzz/fast-check) (TypeScript-native, works with Vitest)

**Configuration:** Minimum 100 iterations per property test (`numRuns: 100`).

**Tag format:** Each property test is tagged with a comment:
```
// Feature: ui, Property <N>: <property_text>
```

**Property test mapping:**

| Property | Test description | Arbitraries |
|---|---|---|
| P1: Auth token round-trip | `fc.string()` for token, `fc.record(...)` for user | `fc.string({ minLength: 1 })`, user record |
| P2: Bearer token attachment | `fc.string({ minLength: 1 })` for token, `fc.record(...)` for request config | token string, Axios request config |
| P3: 401 clears token + redirects | `fc.string({ minLength: 1 })` for token | token string |
| P4: Filters forwarded as query params | `fc.option(fc.constantFrom(...statuses))`, `fc.option(fc.integer())` | filter combinations |
| P5: Work order detail renders all fields | `fc.array(partLineArb)`, `fc.array(laborLineArb)`, `fc.array(historyArb)` | work order with random lines |
| P6: Schedule list renders all schedules | `fc.array(scheduleArb, { minLength: 1 })` | list of schedules |
| P7: 409 error message displayed | `fc.string({ minLength: 1 })` for error message | error message string |
| P8: JWT not rendered in UI | `fc.string({ minLength: 10 })` for token | token string |

### Unit Tests

Unit tests focus on:
- Form rendering (login, register, schedule creation forms have correct fields)
- Successful login flow (mock 200 → navigate to dashboard)
- Status transition submission (mock PATCH → status updates in place)
- Schedule creation success (mock POST → list refreshes)
- 401 on login shows error message (not redirect)
- Dashboard renders three summary sections

### Test Runner

Vitest + React Testing Library. Run with `vitest --run` for single-pass CI execution.
