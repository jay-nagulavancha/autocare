declare global {
  interface Window {
    __ENV?: {
      VITE_AUTH_API_URL?: string;
      VITE_MAINTENANCE_API_URL?: string;
    };
  }
}

/** Prefer window.__ENV (written at container start from K8s / Docker env) over Vite build-time vars. */
export function getAuthApiBase(): string {
  return (
    window.__ENV?.VITE_AUTH_API_URL ??
    import.meta.env.VITE_AUTH_API_URL ??
    'http://localhost:8080'
  );
}

export function getMaintenanceApiBase(): string {
  return (
    window.__ENV?.VITE_MAINTENANCE_API_URL ??
    import.meta.env.VITE_MAINTENANCE_API_URL ??
    'http://localhost:8081'
  );
}

/**
 * Combines api base URL with a path without producing '//api/...' when base is '/'.
 */
export function joinApiPath(apiBase: string, path: string): string {
  const p = path.startsWith('/') ? path : `/${path}`;
  const b = apiBase.trim();
  if (b === '' || b === '/') return p;
  return `${b.replace(/\/$/, '')}${p}`;
}
