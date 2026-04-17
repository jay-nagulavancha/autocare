// ── Auth ──────────────────────────────────────────────────────────────────

export interface SignInRequest {
  username: string;
  password: string;
}

export interface SignInResponse {
  accessToken: string;
  tokenType: string; // "Bearer"
  id: number;
  username: string;
  email: string;
  roles: string[];
}

export interface SignUpRequest {
  username: string;
  email: string;
  password: string;
  role?: string[]; // e.g. ["admin"]
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
  number: number; // current page (0-indexed)
  size: number;
}

// ── Dashboard ─────────────────────────────────────────────────────────────

export interface DashboardSummary {
  openWorkOrderCount: number;
  todaySchedules: ServiceSchedule[];
  bays: Bay[];
}
