import axios from 'axios';
import { useEffect, useState } from 'react';
import { getAuthApiBase, getMaintenanceApiBase, joinApiPath } from '../config/runtimeEnv';
import type { ServiceVersionPayload } from '../types';
import packageJson from '../../package.json';

type LoadState = 'loading' | 'ready' | 'error';

/** First 7 chars of a full git SHA; pass through short or non-hex labels (e.g. `local`). */
function shortGitSha(value: string | undefined | null): string {
  if (value == null || value === '') return '—';
  const v = value.trim();
  if (/^[0-9a-fA-F]{8,64}$/.test(v)) {
    return v.slice(0, 7);
  }
  return v;
}

/** Human-readable UTC date/time label for ISO-8601 `buildTime` from APIs or VITE_BUILD_TIME. */
function formatUtcLabel(iso: string | undefined | null): string | null {
  if (iso == null || iso === '') return null;
  if (iso.trim() === 'unknown') return null;
  const t = Date.parse(iso);
  if (!Number.isNaN(t)) {
    const formatted = new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    }).format(t);
    return `${formatted} UTC`;
  }
  return iso;
}

function formatLine(label: string, payload: ServiceVersionPayload | null, state: LoadState): string {
  if (state === 'loading') return `${label}: …`;
  if (state === 'error' || !payload) return `${label}: unreachable`;
  const ver = payload.version ?? '—';
  const git = shortGitSha(payload.gitCommit);
  const core = `${ver} (${git})`;
  const when = formatUtcLabel(payload.buildTime ?? null);
  return when ? `${label}: ${core} · built ${when}` : `${label}: ${core}`;
}

/** Build / version strip for demo; use from About page after login. */
export default function ServiceVersionsPanel() {
  const uiVersion = packageJson.version;
  const uiBuilt = formatUtcLabel(import.meta.env.VITE_BUILD_TIME ?? null);
  const [auth, setAuth] = useState<ServiceVersionPayload | null>(null);
  const [maint, setMaint] = useState<ServiceVersionPayload | null>(null);
  const [authState, setAuthState] = useState<LoadState>('loading');
  const [maintState, setMaintState] = useState<LoadState>('loading');

  useEffect(() => {
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

  const uiRow = uiBuilt
    ? `UI (vehicle-maintenance-ui): ${uiVersion} · built ${uiBuilt}`
    : `UI (vehicle-maintenance-ui): ${uiVersion}`;

  const rows = [
    uiRow,
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
