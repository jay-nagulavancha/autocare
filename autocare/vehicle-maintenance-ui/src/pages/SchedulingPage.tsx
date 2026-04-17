import { useState, type FormEvent } from 'react';
import NavBar from '../components/NavBar';
import { useSchedules } from '../hooks/useSchedules';
import { maintenanceClient } from '../api/maintenanceClient';
import type { CreateScheduleRequest } from '../types';

function ScheduleForm({ onSuccess }: { onSuccess: () => void }) {
  const [vehicleId, setVehicleId] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [serviceType, setServiceType] = useState('');
  const [bayId, setBayId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const body: CreateScheduleRequest = {
      vehicleId: Number(vehicleId),
      scheduledAt: new Date(scheduledAt).toISOString(),
      serviceType,
    };
    if (bayId) body.bayId = Number(bayId);

    try {
      await maintenanceClient.post('/api/v1/schedules', body);
      setVehicleId('');
      setScheduledAt('');
      setServiceType('');
      setBayId('');
      onSuccess();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number; data?: { message?: string } } };
      if (axiosErr.response?.status === 409) {
        setError(axiosErr.response.data?.message ?? 'Bay conflict.');
      } else {
        setError('Unable to reach the server. Please check your connection.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', maxWidth: 400 }}>
      <h2 style={{ fontSize: '1rem', margin: 0 }}>New Schedule</h2>
      <div>
        <label htmlFor="vehicle-id" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Vehicle ID</label>
        <input
          id="vehicle-id"
          type="number"
          value={vehicleId}
          onChange={(e) => setVehicleId(e.target.value)}
          required
          style={{ width: '100%', padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, boxSizing: 'border-box' }}
        />
      </div>
      <div>
        <label htmlFor="scheduled-at" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Date & Time</label>
        <input
          id="scheduled-at"
          type="datetime-local"
          value={scheduledAt}
          onChange={(e) => setScheduledAt(e.target.value)}
          required
          style={{ width: '100%', padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, boxSizing: 'border-box' }}
        />
      </div>
      <div>
        <label htmlFor="service-type" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Service Type</label>
        <input
          id="service-type"
          type="text"
          value={serviceType}
          onChange={(e) => setServiceType(e.target.value)}
          required
          style={{ width: '100%', padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, boxSizing: 'border-box' }}
        />
      </div>
      <div>
        <label htmlFor="bay-id" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Bay ID (optional)</label>
        <input
          id="bay-id"
          type="number"
          value={bayId}
          onChange={(e) => setBayId(e.target.value)}
          style={{ width: '100%', padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, boxSizing: 'border-box' }}
        />
      </div>
      {error && (
        <p role="alert" style={{ color: '#dc2626', fontSize: '0.875rem', margin: 0 }}>{error}</p>
      )}
      <button
        type="submit"
        disabled={loading}
        style={{ padding: '0.5rem 1rem', background: '#1d4ed8', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', alignSelf: 'flex-start' }}
      >
        {loading ? 'Scheduling…' : 'Create Schedule'}
      </button>
    </form>
  );
}

export default function SchedulingPage() {
  const { data, loading, error, refresh } = useSchedules();

  return (
    <>
      <NavBar />
      <main style={{ padding: '2rem', display: 'grid', gap: '2rem', gridTemplateColumns: '1fr 2fr' }}>
        <ScheduleForm onSuccess={refresh} />

        <section>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.75rem' }}>Schedules</h2>
          {loading && <p>Loading schedules…</p>}
          {error && <p role="alert" style={{ color: '#dc2626' }}>{error}</p>}
          {!loading && !error && data && (
            data.content.length === 0 ? (
              <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No schedules found.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                {data.content.map((s) => (
                  <li
                    key={s.id}
                    data-schedule-id={s.id}
                    style={{ padding: '0.6rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.875rem' }}
                  >
                    <strong>{new Date(s.scheduledAt).toLocaleString()}</strong> — {s.serviceType}{' '}
                    <span style={{ color: '#64748b' }}>({s.vehicle?.vin ?? '—'})</span>
                    {s.bay && <span style={{ color: '#64748b' }}> · Bay {s.bay.name}</span>}
                    <span style={{ marginLeft: '0.5rem', color: '#94a3b8', fontSize: '0.75rem' }}>{s.status}</span>
                  </li>
                ))}
              </ul>
            )
          )}
        </section>
      </main>
    </>
  );
}
