import { useEffect, useState } from 'react';
import axios from 'axios';
import { getAuthApiBase, getMaintenanceApiBase } from '../config/runtimeEnv';
import type { ServiceVersionPayload } from '../types';
import packageJson from '../../package.json';

type LoadState = 'loading' | 'ready' | 'error';

function formatLine(label: string, payload: ServiceVersionPayload | null, state: LoadState): string {
  if (state === 'loading') return `${label}: …`;
  if (state === 'error' || !payload) return `${label}: unreachable`;
  return `${label}: ${payload.version} (${payload.gitCommit})`;
}

export default function ServiceVersionsPanel({ compact = false }: { compact?: boolean }) {
  const uiVersion = packageJson.version;
  const [auth, setAuth] = useState<ServiceVersionPayload | null>(null);
  const [maint, setMaint] = useState<ServiceVersionPayload | null>(null);
  const [authState, setAuthState] = useState<LoadState>('loading');
  const [maintState, setMaintState] = useState<LoadState>('loading');

  useEffect(() => {
    const authBase = getAuthApiBase();
    const maintBase = getMaintenanceApiBase();

    axios
      .get<ServiceVersionPayload>(`${authBase}/api/version`, { timeout: 8000 })
      .then((res) => {
        setAuth(res.data);
        setAuthState('ready');
      })
      .catch(() => {
        setAuth(null);
        setAuthState('error');
      });

    axios
      .get<ServiceVersionPayload>(`${maintBase}/api/version`, { timeout: 8000 })
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
        marginTop: compact ? 0 : '2rem',
        padding: compact ? '0.5rem 1rem' : '1rem',
        background: compact ? '#0f172a' : '#f8fafc',
        color: compact ? '#cbd5e1' : '#334155',
        borderTop: compact ? '1px solid #334155' : '1px solid #e2e8f0',
        fontSize: compact ? '0.7rem' : '0.8rem',
        lineHeight: 1.5,
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: compact ? '0.25rem' : '0.5rem', color: compact ? '#94a3b8' : '#1e293b' }}>
        Build versions
      </div>
      <ul style={{ margin: 0, paddingLeft: '1.1rem' }}>
        {rows.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
    </aside>
  );
}
