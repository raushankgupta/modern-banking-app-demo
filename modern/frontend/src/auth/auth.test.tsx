import { act, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { request, sessionStorageKey } from '../api/client';
import { jsonResponse, summaryFixture } from '../test/fixtures';
import { AuthProvider } from './AuthContext';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function storeSession() {
  window.localStorage.setItem(
    sessionStorageKey,
    JSON.stringify({ token: 'abc', username: 'admin', employeeName: 'Branch Administrator' }),
  );
}

afterEach(() => vi.restoreAllMocks());

describe('protected routes', () => {
  it('redirects to /login when there is no token', () => {
    renderAt('/');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows the protected page when a token is stored', async () => {
    storeSession();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summaryFixture));
    renderAt('/');
    expect(await screen.findByText(/Welcome back, Branch Administrator/)).toBeInTheDocument();
  });
});

describe('API client', () => {
  it('sends the bearer token and the /api/v1 prefix', async () => {
    storeSession();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200 }),
    );

    await request('/dashboard/summary', { query: { type: 'DEPOSIT', empty: undefined } });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/dashboard/summary?type=DEPOSIT');
    expect((init as RequestInit).headers).toMatchObject({ Authorization: 'Bearer abc' });
  });

  it('turns an error body into an ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 'NOT_FOUND', message: 'Account not found' }), { status: 404 }),
    );
    await expect(request('/accounts/1')).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' });
  });

  it('a 401 response clears the token and redirects to /login', async () => {
    storeSession();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summaryFixture));
    renderAt('/');
    expect(await screen.findByText(/Welcome back/)).toBeInTheDocument();

    fetchMock.mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED', message: 'expired' }, 401));
    await act(async () => {
      await expect(request('/dashboard/summary')).rejects.toMatchObject({ status: 401 });
    });

    await waitFor(() => expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument());
    expect(window.localStorage.getItem(sessionStorageKey)).toBeNull();
  });
});
