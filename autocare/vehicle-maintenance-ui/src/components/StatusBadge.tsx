import type { WorkOrderStatus } from '../types';

const statusStyles: Record<WorkOrderStatus, { background: string; color: string }> = {
  OPEN: { background: '#dbeafe', color: '#1d4ed8' },
  IN_PROGRESS: { background: '#fef9c3', color: '#854d0e' },
  PENDING_PARTS: { background: '#ffedd5', color: '#9a3412' },
  COMPLETED: { background: '#dcfce7', color: '#166534' },
  INVOICED: { background: '#f3e8ff', color: '#6b21a8' },
};

interface StatusBadgeProps {
  status: WorkOrderStatus;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const style = statusStyles[status] ?? { background: '#f1f5f9', color: '#475569' };
  return (
    <span
      style={{
        ...style,
        padding: '0.2rem 0.6rem',
        borderRadius: '9999px',
        fontSize: '0.75rem',
        fontWeight: 600,
        display: 'inline-block',
      }}
    >
      {status.replace('_', ' ')}
    </span>
  );
}
