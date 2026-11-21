import type { ApiErrorBody } from './types';

const TOKEN_KEY = 'modernbank.session';

/** Empty by default: requests go to the same origin (Vite proxy in dev, reverse proxy in Docker). */
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly field?: string | null;

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN';
    this.field = body.field;
  }
}

let unauthorizedHandler: (() => void) | null = null;

/** The auth context registers a callback that clears the session when the API answers 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export function getStoredToken(): string | null {
  try {
    const raw = window.localStorage.getItem(TOKEN_KEY);
    return raw ? (JSON.parse(raw) as { token: string }).token : null;
  } catch {
    return null;
  }
}

export const sessionStorageKey = TOKEN_KEY;

export interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  query?: Record<string, string | undefined>;
  /** Login failures are 401 too, but must not trigger the "session expired" redirect. */
  skipUnauthorizedHandler?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value) params.set(key, value);
  }
  const queryString = params.toString();
  const url = `${BASE_URL}/api/v1${path}${queryString ? `?${queryString}` : ''}`;

  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = getStoredToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {};
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      // non-JSON error body: fall back to the generic message
    }
    if (response.status === 401 && !options.skipUnauthorizedHandler) {
      unauthorizedHandler?.();
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
