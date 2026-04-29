import axios from 'axios';
import { useEffect, useState } from 'react';
import { getAuthApiBase, getMaintenanceApiBase, joinApiPath } from '../config/runtimeEnv';
import type { ServiceVersionPayload } from '../types';
import packageJson from '../../package.json';

type LoadState = 'loading' | 'ready' | 'error';

function formatLine(label: string, payload: ServiceVersionPayload | null, state: LoadState): string {
  if (state === 'loading') return `${label}: …`;
  if (state === 'error' || !payload) return `${label}: unreachable`;
  const ver = payload.version ?? '—';
  const git = payload.gitCommit ?? '—';
  return `${label}: ${ver} (${git})`;
}

/** Build / version strip for demo; use from About page after login. */
export default function ServiceVersionsPanel() {
  const uiVersion = packageJson.version;
  const [auth, setAuth] = useState<ServiceVersionPayload | null>(null);
  const [maint, setMaint] = useState<ServiceVersionPayload | null>(null);
  const [authState, setAuthState] = useState<LoadState>('loading');
  const [maintState, setMaintState] = useState<LoadState>('loading');

  useEffect(() => {
    // Paths must align with Ingress: /api/auth/* → user-auth, /api/v1/* → maintenance (/api/version hits the UI).
    const authUrl = joinApiPath(getAuthApiBase(), '/api/auth/version');
    const maintUrl = joinApiPath(getMaintenanceApiBase(), '/api/v1/version');

    axios
      .get<ServiceVersionPayload>(authUrl, { timeout: 8000 })
      .then((res) => {
        setAuth(res.data);
        setAuthState('ready');
      })
      .catch(() => {
        setAuth(null);
        setAuthState('error');
      });

    axios
      .get<ServiceVersionPayload>(maintUrl, { timeout: 8000 })
      .then((res) => {
        setMaint(res.data);
        setMaintState('ready');
      })
      .catch(() => {
        setMaint(null);
        setMaintState('error');
      });
  }, []);

  const rows = [
    `UI (vehicle-maintenance-ui): ${uiVersion}`,
    formatLine('Auth API (user-auth-service)', auth, authState),
    formatLine('Maintenance API (vehicle-maintenance-service)', maint, maintState),
  ];

  return (
    <aside
      aria-label="Application versions"
      style={{
        marginTop: '1rem',
        padding: '1rem',
        background: '#f8fafc',
        color: '#334155',
        border: '1px solid #e2e8f0',
        borderRadius: 8,
        fontSize: '0.875rem',
        lineHeight: 1.6,
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
        maxWidth: 560,
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: '0.5rem', color: '#1e293b' }}>Build versions</div>
      <ul style={{ margin: 0, paddingLeft: '1.25rem' }}>
        {rows.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    </aside>
  );
}
