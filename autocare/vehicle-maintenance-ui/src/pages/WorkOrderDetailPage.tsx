import { useState, type FormEvent } from 'react';
import { useParams, Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import StatusBadge from '../components/StatusBadge';
import { useWorkOrder } from '../hooks/useWorkOrder';
import { maintenanceClient } from '../api/maintenanceClient';
import type { WorkOrderStatus } from '../types';

const STATUS_OPTIONS: WorkOrderStatus[] = ['OPEN', 'IN_PROGRESS', 'PENDING_PARTS', 'COMPLETED', 'INVOICED'];

function StatusTransitionForm({ id, currentStatus, onSuccess }: { id: number; currentStatus: WorkOrderStatus; onSuccess: () => void }) {
  const [newStatus, setNewStatus] = useState<WorkOrderStatus>(currentStatus);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await maintenanceClient.patch(`/api/v1/work-orders/${id}/status`, { status: newStatus });
      onSuccess();
    } catch (err: unknown) {
      const axiosErr = err as { response?: { status?: number; data?: { message?: string } } };
      if (axiosErr.response?.status === 422) {
        setError(axiosErr.response.data?.message ?? 'Invalid status transition.');
      } else {
        setError('Unable to reach the server. Please check your connection.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <div>
        <label htmlFor="new-status" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>
          Transition to
        </label>
        <select
          id="new-status"
          value={newStatus}
          onChange={(e) => setNewStatus(e.target.value as WorkOrderStatus)}
          style={{ padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4 }}
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>{s.replace('_', ' ')}</option>
          ))}
        </select>
      </div>
      <button
        type="submit"
        disabled={loading || newStatus === currentStatus}
        style={{ padding: '0.4rem 1rem', background: '#1d4ed8', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' }}
      >
        {loading ? 'Updating…' : 'Update Status'}
      </button>
      {error && <p role="alert" style={{ color: '#dc2626', fontSize: '0.875rem', width: '100%', margin: 0 }}>{error}</p>}
    </form>
  );
}

export default function WorkOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const workOrderId = Number(id);
  const { data: wo, loading, error, refresh } = useWorkOrder(workOrderId);

  if (loading) {
    return (
      <>
        <NavBar />
        <main style={{ padding: '2rem' }}><p>Loading work order…</p></main>
      </>
    );
  }

  if (error || !wo) {
    return (
      <>
        <NavBar />
        <main style={{ padding: '2rem' }}>
          <p role="alert" style={{ color: '#dc2626' }}>{error ?? 'Work order not found.'}</p>
          <Link to="/work-orders">← Back to list</Link>
        </main>
      </>
    );
  }

  const totalParts = wo.partLines.reduce((sum, p) => sum + p.quantity * p.unitCost, 0);
  const totalLabor = wo.laborLines.reduce((sum, l) => sum + l.hours * l.rate, 0);

  return (
    <>
      <NavBar />
      <main style={{ padding: '2rem', maxWidth: 900, margin: '0 auto' }}>
        <Link to="/work-orders" style={{ fontSize: '0.875rem', color: '#1d4ed8' }}>← Back to list</Link>

        {/* Work Order Header */}
        <section style={{ marginTop: '1rem', marginBottom: '1.5rem' }}>
          <h1 style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>Work Order #{wo.id}</h1>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', fontSize: '0.875rem', color: '#475569' }}>
            <span>Status: <StatusBadge status={wo.status} /></span>
            <span>Vehicle: {wo.vehicle?.vin} ({wo.vehicle?.make} {wo.vehicle?.model} {wo.vehicle?.year})</span>
            <span>Technician: {wo.technician?.name ?? '—'}</span>
            <span>Bay: {wo.bay?.name ?? '—'}</span>
            <span>Created: {new Date(wo.createdAt).toLocaleString()}</span>
          </div>
          <p style={{ marginTop: '0.75rem', color: '#334155' }}>{wo.description}</p>
        </section>

        {/* Part Lines */}
        <section style={{ marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Parts</h2>
          {wo.partLines.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No parts.</p>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ background: '#f8fafc', textAlign: 'left' }}>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Part</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Qty</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Unit Cost</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {wo.partLines.map((p) => (
                  <tr key={p.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{p.partName}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{p.quantity}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>${p.unitCost.toFixed(2)}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>${(p.quantity * p.unitCost).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {/* Labor Lines */}
        <section style={{ marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Labor</h2>
          {wo.laborLines.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No labor entries.</p>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ background: '#f8fafc', textAlign: 'left' }}>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Description</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Hours</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Rate</th>
                  <th style={{ padding: '0.5rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {wo.laborLines.map((l) => (
                  <tr key={l.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{l.description}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{l.hours}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>${l.rate.toFixed(2)}/hr</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>${(l.hours * l.rate).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {/* Cost Summary */}
        <section style={{ marginBottom: '1.5rem', background: '#f8fafc', padding: '1rem', borderRadius: 8 }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Cost Summary</h2>
          <div style={{ fontSize: '0.875rem', display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
            <span>Parts: ${totalParts.toFixed(2)}</span>
            <span>Labor: ${totalLabor.toFixed(2)}</span>
            <strong style={{ marginTop: '0.25rem' }}>Total: ${wo.totalCost.toFixed(2)}</strong>
          </div>
        </section>

        {/* Status History */}
        <section style={{ marginBottom: '1.5rem' }}>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.5rem' }}>Status History</h2>
          {wo.statusHistory.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No history.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, fontSize: '0.875rem' }}>
              {wo.statusHistory.map((h) => (
                <li key={h.id} style={{ padding: '0.4rem 0', borderBottom: '1px solid #f1f5f9' }}>
                  {new Date(h.changedAt).toLocaleString()} — {h.previousStatus ?? 'NEW'} → <StatusBadge status={h.newStatus} /> by {h.changedBy}
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Status Transition Form */}
        <section>
          <h2 style={{ fontSize: '1rem', marginBottom: '0.75rem' }}>Update Status</h2>
          <StatusTransitionForm id={wo.id} currentStatus={wo.status} onSuccess={refresh} />
        </section>
      </main>
    </>
  );
}
