import { useEffect, useState } from 'react';
import { maintenanceClient } from '../api/maintenanceClient';
import type { Page, WorkOrder, WorkOrderListParams } from '../types';

interface UseWorkOrdersResult {
  data: Page<WorkOrder> | null;
  loading: boolean;
  error: string | null;
}

export function useWorkOrders(params: WorkOrderListParams = {}): UseWorkOrdersResult {
  const [data, setData] = useState<Page<WorkOrder> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Build a stable key from params to trigger re-fetch when filters change
  const paramsKey = JSON.stringify(params);

  useEffect(() => {
    setLoading(true);
    setError(null);

    // Strip undefined values so they are not sent as query params
    const cleanParams: Record<string, string | number> = {};
    if (params.status !== undefined) cleanParams.status = params.status;
    if (params.vehicleId !== undefined) cleanParams.vehicleId = params.vehicleId;
    if (params.technicianId !== undefined) cleanParams.technicianId = params.technicianId;
    if (params.page !== undefined) cleanParams.page = params.page;
    if (params.size !== undefined) cleanParams.size = params.size;

    maintenanceClient
      .get<Page<WorkOrder>>('/api/v1/work-orders', { params: cleanParams })
      .then((res) => setData(res.data))
      .catch(() => setError('Unable to reach the server. Please check your connection.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramsKey]);

  return { data, loading, error };
}
