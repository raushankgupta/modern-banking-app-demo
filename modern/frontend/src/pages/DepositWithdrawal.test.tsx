import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { sessionStorageKey } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { jsonResponse } from '../test/fixtures';

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

const receipt = (type: string, balance: number) => ({
  accountNumber: '1001000001',
  transactionType: type,
  amount: 250,
  balance,
  reference: type === 'DEPOSIT' ? 'DEP0000000007' : 'WDR0000000008',
});

afterEach(() => vi.restoreAllMocks());

describe.each([
  { name: 'Cash deposit', path: '/deposit', button: 'Deposit', endpoint: 'deposits', type: 'DEPOSIT', ok: 'Deposit successful' },
  { name: 'Cash withdrawal', path: '/withdraw', button: 'Withdraw', endpoint: 'withdrawals', type: 'WITHDRAWAL', ok: 'Withdrawal successful' },
])('$name screen', ({ name, path, button, endpoint, type, ok }) => {
  it('renders the form and prefills the account from the query string', () => {
    renderAt(`${path}?account=1001000001`);
    expect(screen.getByRole('heading', { name })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Account number' })).toHaveValue('1001000001');
    expect(screen.getByRole('textbox', { name: 'Amount' })).toHaveValue('');
    expect(screen.getByRole('textbox', { name: /Description/ })).toBeInTheDocument();
  });

  it('validates account number and amount on the client without calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderAt(path);

    await userEvent.click(screen.getByRole('button', { name: button }));
    expect(await screen.findByText('Please enter the account number.')).toBeInTheDocument();
    expect(screen.getByText('Please enter an amount.')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', { name: 'Account number' }), '1001000001');
    for (const [input, message] of [
      ['0', 'Amount must be greater than zero.'],
      ['12.345', 'Amount cannot have more than two decimal places.'],
      ['10000000.01', 'Amount cannot exceed 10,000,000.00 per transaction.'],
    ]) {
      const amount = screen.getByRole('textbox', { name: 'Amount' });
      await userEvent.clear(amount);
      await userEvent.type(amount, input);
      await userEvent.click(screen.getByRole('button', { name: button }));
      expect(await screen.findByText(message)).toBeInTheDocument();
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('submits and shows the reference number and new balance', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(receipt(type, 5250), 201));
    renderAt(`${path}?account=1001000001`);

    await userEvent.type(screen.getByRole('textbox', { name: 'Amount' }), '250');
    await userEvent.type(screen.getByRole('textbox', { name: /Description/ }), 'Counter');
    await userEvent.click(screen.getByRole('button', { name: button }));

    expect(await screen.findByText(ok)).toBeInTheDocument();
    expect(screen.getByText(/Reference (DEP0000000007|WDR0000000008)\. New balance: 5,250\.00/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/accounts/1001000001/${endpoint}`,
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ amount: '250', description: 'Counter' }) }),
    );
    // form is reset for the next transaction but keeps the account
    expect(screen.getByRole('textbox', { name: 'Amount' })).toHaveValue('');
    expect(screen.getByRole('textbox', { name: 'Account number' })).toHaveValue('1001000001');
  });

  it('shows a server error clearly and keeps the form editable', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'ACCOUNT_NOT_ACTIVE', message: 'Account 1001000008 is FROZEN.' }, 409),
    );
    renderAt(`${path}?account=1001000008`);

    await userEvent.type(screen.getByRole('textbox', { name: 'Amount' }), '50');
    await userEvent.click(screen.getByRole('button', { name: button }));

    expect(await screen.findByText('Account 1001000008 is FROZEN.')).toBeInTheDocument();
    expect(screen.queryByText(ok)).not.toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Amount' })).toHaveValue('50');
    expect(screen.getByRole('textbox', { name: 'Amount' })).toBeEnabled();
    expect(screen.getByRole('button', { name: button })).toBeEnabled();
  });
});

describe('withdrawal specifics', () => {
  it('shows an insufficient-funds error from the server', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'INSUFFICIENT_FUNDS', message: 'Insufficient funds. Available balance: 100.00' }, 409),
    );
    renderAt('/withdraw?account=1001000001');

    await userEvent.type(screen.getByRole('textbox', { name: 'Amount' }), '500');
    await userEvent.click(screen.getByRole('button', { name: 'Withdraw' }));

    expect(await screen.findByText('Insufficient funds. Available balance: 100.00')).toBeInTheDocument();
  });

  it('maps a server-side amount validation error to the amount field', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'VALIDATION_ERROR', message: 'Amount must be greater than zero.', field: 'amount' }, 400),
    );
    renderAt('/deposit?account=1001000001');

    await userEvent.type(screen.getByRole('textbox', { name: 'Amount' }), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Deposit' }));

    expect(await screen.findByText('Amount must be greater than zero.')).toBeInTheDocument();
  });
});
