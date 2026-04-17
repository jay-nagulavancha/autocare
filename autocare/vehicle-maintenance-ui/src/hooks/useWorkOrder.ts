import { useEffect, useState, useCallback } from 'react';
import { maintenanceClient } from '../api/maintenanceClient';
import type { WorkOrder } from '../types';

interface UseWorkOrderResult {
  data: WorkOrder | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useWorkOrder(id: number): UseWorkOrderResult {
  const [data, setData] = useState<WorkOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    setLoading(true);
    setError(null);

    maintenanceClient
      .get<WorkOrder>(`/api/v1/work-orders/${id}`)
      .then((res) => setData(res.data))
      .catch(() => setError('Unable to reach the server. Please check your connection.'))
      .finally(() => setLoading(false));
  }, [id, tick]);

  return { data, loading, error, refresh };
}
