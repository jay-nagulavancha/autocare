import { useEffect, useState, useCallback } from 'react';
import { maintenanceClient } from '../api/maintenanceClient';
import type { Page, ServiceSchedule } from '../types';

interface UseSchedulesResult {
  data: Page<ServiceSchedule> | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useSchedules(): UseSchedulesResult {
  const [data, setData] = useState<Page<ServiceSchedule> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    setLoading(true);
    setError(null);

    maintenanceClient
      .get<Page<ServiceSchedule>>('/api/v1/schedules')
      .then((res) => setData(res.data))
      .catch(() => setError('Unable to reach the server. Please check your connection.'))
      .finally(() => setLoading(false));
  }, [tick]);

  return { data, loading, error, refresh };
}
