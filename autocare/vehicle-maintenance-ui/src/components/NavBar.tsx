import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function NavBar() {
  const { logout, user } = useAuth();

  return (
    <nav style={{ display: 'flex', gap: '1rem', padding: '0.75rem 1rem', background: '#1e293b', color: '#f8fafc' }}>
      <Link to="/" style={{ color: '#f8fafc', textDecoration: 'none', fontWeight: 600 }}>
        AutoCare
      </Link>
      <Link to="/work-orders" style={{ color: '#cbd5e1', textDecoration: 'none' }}>
        Work Orders
      </Link>
      <Link to="/schedules" style={{ color: '#cbd5e1', textDecoration: 'none' }}>
        Schedules
      </Link>
      <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        {user && <span style={{ color: '#94a3b8', fontSize: '0.875rem' }}>{user.username}</span>}
        <button
          onClick={logout}
          style={{
            background: 'transparent',
            border: '1px solid #475569',
            color: '#f8fafc',
            padding: '0.25rem 0.75rem',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          Logout
        </button>
      </span>
    </nav>
  );
}
