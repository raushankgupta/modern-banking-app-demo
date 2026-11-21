import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { sessionStorageKey } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { historyFixture, jsonResponse } from '../test/fixtures';

function renderAt(path: string) {
  window.localStorage.setItem(
    sessionStorageKey,
    JSON.stringify({ token: 'abc', username: 'admin', employeeName: 'Branch Administrator' }),
  );
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const accountBox = () => screen.getByRole('textbox', { name: 'Account number' });

afterEach(() => vi.restoreAllMocks());

describe('Transaction history screen', () => {
  it('lists transactions newest first with Tags and formatted amounts', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(historyFixture));
    renderAt('/history');

    await userEvent.type(accountBox(), '1001000001');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    const table = await screen.findByRole('table', { name: /^Transaction history - account / });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/accounts/1001000001/transactions', expect.anything());

    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    // server order is kept: newest (transfer) first, oldest (deposit) last
    expect(within(rows[1]).getByText('Transfer out')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Withdrawal')).toBeInTheDocument();
    expect(within(rows[3]).getByText('Deposit')).toBeInTheDocument();
    expect(within(rows[1]).getByText('1,200.00')).toBeInTheDocument();
    expect(within(rows[3]).getByText('5,000.00')).toBeInTheDocument();
    expect(within(rows[1]).getByText('TRF0000000006')).toBeInTheDocument();
    expect(within(rows[2]).getByText('ATM-style cash withdrawal')).toBeInTheDocument();
  });

  it('filters by type through the API, with all five filter options', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(historyFixture))
      .mockResolvedValueOnce(jsonResponse([historyFixture[2]]))
      .mockResolvedValueOnce(jsonResponse(historyFixture));
    renderAt('/history?account=1001000001');

    expect(await screen.findByRole('table', { name: /^Transaction history - account / })).toBeInTheDocument();
    const select = screen.getByRole('combobox', { name: 'Type' });
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'All',
      'Deposit',
      'Withdrawal',
      'Transfer out',
      'Transfer in',
    ]);

    await userEvent.selectOptions(select, 'DEPOSIT');
    await vi.waitFor(() =>
      expect(within(screen.getByRole('table', { name: /^Transaction history - account / })).getAllByRole('row')).toHaveLength(2),
    );
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/accounts/1001000001/transactions?type=DEPOSIT',
      expect.anything(),
    );

    await userEvent.selectOptions(select, 'ALL');
    await vi.waitFor(() =>
      expect(within(screen.getByRole('table', { name: /^Transaction history - account / })).getAllByRole('row')).toHaveLength(4),
    );
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/accounts/1001000001/transactions', expect.anything());
  });

  it('loads straight away when opened with ?account=', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(historyFixture));
    renderAt('/history?account=1001000001');

    expect(await screen.findByRole('table', { name: /^Transaction history - account / })).toBeInTheDocument();
    expect(accountBox()).toHaveValue('1001000001');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('shows an empty state for an account without history, and for a filter with no matches', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]));
    renderAt('/history');

    await userEvent.type(accountBox(), '1001000002');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Account 1001000002 has no transactions yet.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: /^Transaction history - account / })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Type' }), 'DEPOSIT');
    expect(await screen.findByText('Account 1001000002 has no transactions of this type.')).toBeInTheDocument();
  });

  it('shows the not-found error for an unknown account and clears the old table', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(historyFixture))
      .mockResolvedValueOnce(jsonResponse({ code: 'NOT_FOUND', message: 'Account 9999999999 was not found.' }, 404));
    renderAt('/history?account=1001000001');
    expect(await screen.findByRole('table', { name: /^Transaction history - account / })).toBeInTheDocument();

    await userEvent.clear(accountBox());
    await userEvent.type(accountBox(), '9999999999');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Account 9999999999 was not found.')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: /^Transaction history - account / })).not.toBeInTheDocument();
  });

  it('validates the account number on the client without calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderAt('/history');

    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Please enter the account number.')).toBeInTheDocument();

    await userEvent.type(accountBox(), '10-01');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText(/may only contain letters and digits/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
