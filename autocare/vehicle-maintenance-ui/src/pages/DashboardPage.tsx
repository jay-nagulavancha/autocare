import { useEffect, useState } from 'react';
import { maintenanceClient } from '../api/maintenanceClient';
import NavBar from '../components/NavBar';
import StatusBadge from '../components/StatusBadge';
import type { Page, WorkOrder, ServiceSchedule, Bay } from '../types';

export default function DashboardPage() {
  const [openWorkOrders, setOpenWorkOrders] = useState<WorkOrder[]>([]);
  const [schedules, setSchedules] = useState<ServiceSchedule[]>([]);
  const [bays, setBays] = useState<Bay[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];

    Promise.all([
      maintenanceClient.get<Page<WorkOrder>>('/api/v1/work-orders', { params: { status: 'OPEN', size: 10 } }),
      maintenanceClient.get<Page<ServiceSchedule>>('/api/v1/schedules', { params: { date: today, size: 20 } }),
      maintenanceClient.get<Bay[]>('/api/v1/bays'),
    ])
      .then(([woRes, schedRes, baysRes]) => {
        setOpenWorkOrders(woRes.data.content);
        setSchedules(schedRes.data.content);
        setBays(baysRes.data);
      })
      .catch(() => {
        setError('Unable to reach the server. Please check your connection.');
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <>
        <NavBar />
        <main style={{ padding: '2rem', textAlign: 'center' }}>
          <p>Loading dashboard…</p>
        </main>
      </>
    );
  }

  if (error) {
    return (
      <>
        <NavBar />
        <main style={{ padding: '2rem' }}>
          <p role="alert" style={{ color: '#dc2626' }}>{error}</p>
        </main>
      </>
    );
  }

  return (
    <>
      <NavBar />
      <main style={{ padding: '2rem', display: 'grid', gap: '1.5rem', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
        {/* Open Work Orders Summary */}
        <section aria-label="Open Work Orders" style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.75rem', color: '#1e293b' }}>
            Open Work Orders ({openWorkOrders.length})
          </h2>
          {openWorkOrders.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No open work orders.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {openWorkOrders.map((wo) => (
                <li key={wo.id} style={{ padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.875rem' }}>
                  <span style={{ fontWeight: 500 }}>#{wo.id}</span> — {wo.vehicle?.vin ?? '—'}{' '}
                  <StatusBadge status={wo.status} />
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Today's Schedules Summary */}
        <section aria-label="Today's Schedules" style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.75rem', color: '#1e293b' }}>
            Today's Schedules ({schedules.length})
          </h2>
          {schedules.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No schedules for today.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {schedules.map((s) => (
                <li key={s.id} style={{ padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.875rem' }}>
                  {new Date(s.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} —{' '}
                  {s.serviceType} ({s.vehicle?.vin ?? '—'})
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Bay Availability Summary */}
        <section aria-label="Bay Availability" style={{ border: '1px solid #e2e8f0', borderRadius: 8, padding: '1rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.75rem', color: '#1e293b' }}>
            Bays ({bays.length})
          </h2>
          {bays.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No bays configured.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {bays.map((bay) => (
                <li key={bay.id} style={{ padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.875rem', display: 'flex', justifyContent: 'space-between' }}>
                  <span>{bay.name}</span>
                  <span style={{ color: bay.available ? '#16a34a' : '#dc2626', fontWeight: 500 }}>
                    {bay.available ? 'Available' : 'Occupied'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </>
  );
}
