import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { sessionStorageKey } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { jsonResponse, summaryFixture } from '../test/fixtures';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const okResponse = {
  token: 'jwt-token',
  tokenType: 'Bearer',
  expiresAt: '2030-01-01T00:00:00Z',
  employeeName: 'Branch Administrator',
  username: 'admin',
};

afterEach(() => vi.restoreAllMocks());

describe('LoginPage', () => {
  it('shows labeled fields and a Sign in button', () => {
    renderLogin();
    expect(screen.getByLabelText('Username')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('does not submit empty fields', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(await screen.findByText('Please enter username and password.')).toBeInTheDocument();
  });

  it('signs in with valid credentials and goes to the dashboard', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(okResponse))
      .mockResolvedValue(jsonResponse(summaryFixture));
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'admin123');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/Welcome back, Branch Administrator/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ username: 'admin', password: 'admin123' }) }),
    );
    expect(window.localStorage.getItem(sessionStorageKey)).toContain('jwt-token');
  });

  it('shows an inline error and stays on the page when the login fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 'AUTHENTICATION_FAILED', message: 'Invalid username or password.' }), {
        status: 401,
      }),
    );
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(window.localStorage.getItem(sessionStorageKey)).toBeNull();
  });

  it('shows a loading state and prevents double submit while signing in', async () => {
    let resolve!: (r: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise<Response>((r) => (resolve = r)));
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'admin123{Enter}');

    expect(await screen.findByText('Signing in...')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fetchMock.mockResolvedValue(jsonResponse(summaryFixture));
    resolve(jsonResponse(okResponse));
    await waitFor(() => expect(screen.getByText(/Welcome back/)).toBeInTheDocument());
  });
});
