declare global {
  interface Window {
    __ENV?: {
      VITE_AUTH_API_URL?: string;
      VITE_MAINTENANCE_API_URL?: string;
    };
  }
}

/** `??` preserves "" which breaks Axios baseURL — treat blank as absent. Keeps '/' (same-origin). */
function pickBase(injected?: string, buildTime?: string, fallback?: string): string {
  const i = injected?.trim();
  if (i !== undefined && i !== '') return i;
  const b = typeof buildTime === 'string' ? buildTime.trim() : '';
  if (b !== '') return b;
  return fallback!;
}

/** Prefer window.__ENV (written at container start from K8s / Docker env) over Vite build-time vars. */
export function getAuthApiBase(): string {
  return pickBase(
    window.__ENV?.VITE_AUTH_API_URL,
    import.meta.env.VITE_AUTH_API_URL as string | undefined,
    'http://localhost:8080',
  );
}

export function getMaintenanceApiBase(): string {
  return pickBase(
    window.__ENV?.VITE_MAINTENANCE_API_URL,
    import.meta.env.VITE_MAINTENANCE_API_URL as string | undefined,
    'http://localhost:8081',
  );
}
