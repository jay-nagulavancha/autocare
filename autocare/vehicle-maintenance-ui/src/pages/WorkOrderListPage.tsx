import { useState } from 'react';
import { Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import StatusBadge from '../components/StatusBadge';
import { useWorkOrders } from '../hooks/useWorkOrders';
import type { WorkOrder, WorkOrderStatus, WorkOrderListParams } from '../types';

const STATUS_OPTIONS: WorkOrderStatus[] = ['OPEN', 'IN_PROGRESS', 'PENDING_PARTS', 'COMPLETED', 'INVOICED'];

export default function WorkOrderListPage() {
  const [statusFilter, setStatusFilter] = useState<WorkOrderStatus | ''>('');
  const [vehicleIdFilter, setVehicleIdFilter] = useState('');
  const [technicianIdFilter, setTechnicianIdFilter] = useState('');

  const params: WorkOrderListParams = {};
  if (statusFilter) params.status = statusFilter;
  if (vehicleIdFilter) params.vehicleId = Number(vehicleIdFilter);
  if (technicianIdFilter) params.technicianId = Number(technicianIdFilter);

  const { data, loading, error } = useWorkOrders(params);

  return (
    <>
      <NavBar />
      <main style={{ padding: '2rem' }}>
        <h1 style={{ fontSize: '1.5rem', marginBottom: '1.5rem' }}>Work Orders</h1>

        {/* Filters */}
        <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
          <div>
            <label htmlFor="status-filter" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Status</label>
            <select
              id="status-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as WorkOrderStatus | '')}
              style={{ padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4 }}
            >
              <option value="">All</option>
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>{s.replace('_', ' ')}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="vehicle-filter" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Vehicle ID</label>
            <input
              id="vehicle-filter"
              type="number"
              value={vehicleIdFilter}
              onChange={(e) => setVehicleIdFilter(e.target.value)}
              placeholder="Any"
              style={{ padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, width: 100 }}
            />
          </div>
          <div>
            <label htmlFor="technician-filter" style={{ display: 'block', fontSize: '0.75rem', marginBottom: '0.25rem', color: '#64748b' }}>Technician ID</label>
            <input
              id="technician-filter"
              type="number"
              value={technicianIdFilter}
              onChange={(e) => setTechnicianIdFilter(e.target.value)}
              placeholder="Any"
              style={{ padding: '0.4rem 0.6rem', border: '1px solid #cbd5e1', borderRadius: 4, width: 100 }}
            />
          </div>
        </div>

        {loading && <p>Loading work orders…</p>}
        {error && <p role="alert" style={{ color: '#dc2626' }}>{error}</p>}

        {!loading && !error && data && (
          <>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ background: '#f8fafc', textAlign: 'left' }}>
                  <th style={{ padding: '0.6rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>ID</th>
                  <th style={{ padding: '0.6rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>VIN</th>
                  <th style={{ padding: '0.6rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Status</th>
                  <th style={{ padding: '0.6rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Technician</th>
                  <th style={{ padding: '0.6rem 0.75rem', borderBottom: '1px solid #e2e8f0' }}>Created</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((wo: WorkOrder) => (
                  <tr key={wo.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.6rem 0.75rem' }}>
                      <Link to={`/work-orders/${wo.id}`} style={{ color: '#1d4ed8' }}>#{wo.id}</Link>
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem' }}>{wo.vehicle?.vin ?? '—'}</td>
                    <td style={{ padding: '0.6rem 0.75rem' }}><StatusBadge status={wo.status} /></td>
                    <td style={{ padding: '0.6rem 0.75rem' }}>{wo.technician?.name ?? '—'}</td>
                    <td style={{ padding: '0.6rem 0.75rem' }}>{new Date(wo.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={5} style={{ padding: '1rem 0.75rem', color: '#64748b', textAlign: 'center' }}>
                      No work orders found.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            <p style={{ marginTop: '0.75rem', fontSize: '0.75rem', color: '#64748b' }}>
              {data.totalElements} total — page {data.number + 1} of {data.totalPages}
            </p>
          </>
        )}
      </main>
    </>
  );
}
