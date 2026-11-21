import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { sessionStorageKey } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { jsonResponse, summaryFixture } from '../test/fixtures';

function renderDashboard() {
  window.localStorage.setItem(
    sessionStorageKey,
    JSON.stringify({ token: 'abc', username: 'admin', employeeName: 'Branch Administrator' }),
  );
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => vi.restoreAllMocks());

describe('DashboardPage', () => {
  it('shows KPI tiles and recent transactions from /dashboard/summary', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summaryFixture));
    renderDashboard();

    expect(await screen.findByText('303,304.00')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dashboard/summary', expect.anything());

    const table = screen.getByRole('table', { name: 'Recent transactions' });
    expect(within(table).getAllByRole('row')).toHaveLength(1 + summaryFixture.recentTransactions.length);
    expect(within(table).getByText('Transfer out')).toBeInTheDocument();
    expect(within(table).getByText('Withdrawal')).toBeInTheDocument();
    expect(within(table).getByText('Deposit')).toBeInTheDocument();
    expect(within(table).getByText('1,200.00')).toBeInTheDocument();
    expect(within(table).getByText('TRF0000000006')).toBeInTheDocument();
  });

  it('shows skeletons while loading', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}));
    renderDashboard();

    expect(screen.queryByRole('table', { name: 'Recent transactions' })).not.toBeInTheDocument();
    expect(document.querySelector('.cds--skeleton')).not.toBeNull();
  });

  it('shows an inline error with a working retry when loading fails', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ code: 'INTERNAL_ERROR', message: 'Database unavailable' }, 500))
      .mockResolvedValue(jsonResponse(summaryFixture));
    renderDashboard();

    expect(await screen.findByText('Database unavailable')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('303,304.00')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('navigates from a quick action to the matching screen', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summaryFixture));
    renderDashboard();
    await screen.findByText('303,304.00');

    const quickActions = screen.getByRole('region', { name: 'Quick actions' });
    await userEvent.click(within(quickActions).getByRole('link', { name: /Fund transfer/ }));

    expect(await screen.findByRole('heading', { name: 'Fund transfer' })).toBeInTheDocument();
  });

  it('navigates from the side nav and logs out', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(summaryFixture));
    renderDashboard();
    await screen.findByText('303,304.00');

    const nav = screen.getByRole('navigation', { name: 'Main navigation' });
    await userEvent.click(within(nav).getByRole('link', { name: 'Transaction history' }));
    expect(await screen.findByRole('heading', { name: 'Transaction history' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    expect(window.localStorage.getItem(sessionStorageKey)).toBeNull();
  });
});
