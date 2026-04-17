# Autocare Postman Tests

## Quick Start

1. Import `autocare.postman_collection.json` and `autocare.postman_environment.json` into Postman
2. Select the "Autocare Local" environment
3. Run the collection — it will:
   - Register an admin user with a unique timestamp suffix
   - Login and store the JWT
   - Create a vehicle, work order, parts, labor, technician, bay, schedule
   - Assert all responses
   - Clean up is automatic on re-run (unique RUN_ID per run)

## CLI (Newman)

```bash
npm install -g newman
newman run autocare.postman_collection.json -e autocare.postman_environment.json
```

## Re-runnability

Each test run generates a unique `RUN_ID` (timestamp-based) so usernames, VINs, and entity names never conflict. Safe to run multiple times without manual cleanup.

## Coverage

- Auth: signup (admin/tech/customer), signin, JWT validation
- Vehicles: create, get, list, update, soft-delete, VIN validation, customer isolation
- Work Orders: create, get, list, status transitions (full lifecycle), assign technician/bay
- Parts & Labor: add/remove, cost calculation, closed work order rejection
- Schedules: create, list, bay conflict detection, cancel
- Technicians: create, list, deactivate
- Bays: create, list, deactivate
