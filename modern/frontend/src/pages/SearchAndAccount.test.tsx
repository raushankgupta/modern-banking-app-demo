import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { sessionStorageKey } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { accountFixture, customerFixture, jsonResponse } from '../test/fixtures';

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

afterEach(() => vi.restoreAllMocks());

describe('Customer search', () => {
  it('searches by customer ID and shows customer info and the accounts table', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(customerFixture));
    renderAt('/search');

    await userEvent.type(screen.getByRole('textbox', { name: 'Customer ID' }), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('priya@example.com')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/customers/3', expect.anything());
    expect(screen.getByText('12 Park Street')).toBeInTheDocument();

    const table = screen.getByRole('table', { name: 'Customer accounts' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).getByText('15,000.50')).toBeInTheDocument();
    expect(within(table).getByText('Fixed deposit')).toBeInTheDocument();
    expect(within(table).getByText('Active')).toBeInTheDocument();
    expect(within(table).getByText('Frozen')).toBeInTheDocument();
  });

  it('searches by account number', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(customerFixture));
    renderAt('/search');

    await userEvent.click(screen.getByRole('radio', { name: 'Account number' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Account number' }), '1001000006');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('priya@example.com')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/customers/by-account/1001000006', expect.anything());
  });

  it('shows a friendly message for an empty search without calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderAt('/search');

    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Please enter a search value.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the server message for not-found and invalid input', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ code: 'NOT_FOUND', message: 'Customer 999 was not found.' }, 404))
      .mockResolvedValueOnce(
        jsonResponse({ code: 'VALIDATION_ERROR', message: 'Customer ID must be a number.', field: 'customerId' }, 400),
      );
    renderAt('/search');

    await userEvent.type(screen.getByRole('textbox', { name: 'Customer ID' }), '999');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Customer 999 was not found.')).toBeInTheDocument();

    await userEvent.clear(screen.getByRole('textbox', { name: 'Customer ID' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Customer ID' }), 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Customer ID must be a number.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('links each account to its details page', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(customerFixture))
      .mockResolvedValue(jsonResponse(accountFixture));
    renderAt('/search');

    await userEvent.type(screen.getByRole('textbox', { name: 'Customer ID' }), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await userEvent.click(await screen.findByRole('link', { name: '1001000006' }));

    expect(await screen.findByRole('heading', { name: 'Account details' })).toBeInTheDocument();
  });
});

describe('Account details', () => {
  it('shows type, status, balance and owner with Deposit, Withdraw and History actions', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(accountFixture));
    renderAt('/accounts/1001000006');

    expect(await screen.findByText('15,000.50')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/accounts/1001000006', expect.anything());
    const info = screen.getByLabelText('Account information');
    expect(within(info).getByText('Savings')).toBeInTheDocument();
    expect(within(info).getByText('Active')).toBeInTheDocument();
    expect(within(info).getByText('Priya Sharma')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Deposit' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Withdraw' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'History' })).toBeInTheDocument();
  });

  it('opens the target screen for the account when an action is clicked', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(accountFixture));
    renderAt('/accounts/1001000006');
    await screen.findByText('15,000.50');

    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }));

    expect(await screen.findByRole('heading', { name: 'Cash deposit' })).toBeInTheDocument();
  });

  it('shows a friendly message when the account is not found', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'NOT_FOUND', message: 'Account 123 was not found.' }, 404),
    );
    renderAt('/accounts/123');

    expect(await screen.findByText('Account 123 was not found.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Deposit' })).not.toBeInTheDocument();
  });
});
