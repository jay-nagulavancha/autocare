import { useEffect, useState } from 'react';
import { authClient } from '../api/authClient';
import { maintenanceClient } from '../api/maintenanceClient';
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

/** Fixed bottom-right footer; shown on every route for demo build visibility. */
export default function ServiceVersionsPanel() {
  const uiVersion = packageJson.version;
  const [auth, setAuth] = useState<ServiceVersionPayload | null>(null);
  const [maint, setMaint] = useState<ServiceVersionPayload | null>(null);
  const [authState, setAuthState] = useState<LoadState>('loading');
  const [maintState, setMaintState] = useState<LoadState>('loading');

  useEffect(() => {
    // Use shared axios clients (same base URL merge as rest of UI). Avoid
    // `${base}/api/version` when base is '/' — it becomes '//api/version' which
    // browsers treat as protocol-relative host "api".

    authClient
      .get<ServiceVersionPayload>('/api/version', { timeout: 8000 })
      .then((res) => {
        setAuth(res.data);
        setAuthState('ready');
      })
      .catch(() => {
        setAuth(null);
        setAuthState('error');
      });

    maintenanceClient
      .get<ServiceVersionPayload>('/api/version', { timeout: 8000 })
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
        position: 'fixed',
        bottom: 0,
        right: 0,
        zIndex: 1000,
        maxWidth: 'min(440px, calc(100vw - 16px))',
        margin: 0,
        padding: '0.5rem 0.75rem 0.65rem 1rem',
        background: '#0f172a',
        color: '#cbd5e1',
        borderTop: '1px solid #334155',
        borderLeft: '1px solid #334155',
        borderTopLeftRadius: 8,
        fontSize: '0.7rem',
        lineHeight: 1.5,
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
        boxShadow: '0 -4px 24px rgba(15, 23, 42, 0.35)',
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: '0.25rem', color: '#94a3b8' }}>
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
