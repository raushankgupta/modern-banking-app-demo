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

const field = (name: string | RegExp) => screen.getByRole('textbox', { name });
const submit = () => userEvent.click(screen.getByRole('button', { name: 'Transfer' }));

const receipt = {
  fromAccount: '1001000006',
  toAccount: '1001000011',
  amount: 1200,
  fromBalance: 13800.5,
  toBalance: 4200,
  reference: 'TRF0000000007',
};

afterEach(() => vi.restoreAllMocks());

describe('Transfer screen', () => {
  it('renders the form and prefills the source account from the query string', () => {
    renderAt('/transfer?account=1001000006');
    expect(screen.getByRole('heading', { name: 'Fund transfer' })).toBeInTheDocument();
    expect(field('From account')).toHaveValue('1001000006');
    expect(field('To account')).toHaveValue('');
    expect(field('Amount')).toHaveValue('');
    expect(field(/Description/)).toBeInTheDocument();
  });

  it('validates required fields and the amount without calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderAt('/transfer');

    await submit();
    expect(await screen.findByText('Please enter the source account number.')).toBeInTheDocument();
    expect(screen.getByText('Please enter the destination account number.')).toBeInTheDocument();
    expect(screen.getByText('Please enter an amount.')).toBeInTheDocument();

    await userEvent.type(field('From account'), '1001000006');
    await userEvent.type(field('To account'), '1001000011');
    await userEvent.type(field('Amount'), '12.345');
    await submit();
    expect(await screen.findByText('Amount cannot have more than two decimal places.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('blocks identical source and destination accounts on the client', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    renderAt('/transfer');

    await userEvent.type(field('From account'), '1001000006');
    await userEvent.type(field('To account'), ' 1001000006 ');
    await userEvent.type(field('Amount'), '10');
    await submit();

    expect(await screen.findByText('Source and destination accounts must be different.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('submits and shows the shared reference number and both balances', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(receipt, 201));
    renderAt('/transfer?account=1001000006');

    await userEvent.type(field('To account'), '1001000011');
    await userEvent.type(field('Amount'), '1200');
    await userEvent.type(field(/Description/), 'Rent');
    await submit();

    expect(await screen.findByText('Transfer successful')).toBeInTheDocument();
    expect(screen.getByText(/Reference TRF0000000007/)).toBeInTheDocument();
    expect(screen.getByText(/Balance of 1001000006: 13,800\.50/)).toBeInTheDocument();
    expect(screen.getByText(/Balance of 1001000011: 4,200\.00/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/transfers',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ fromAccount: '1001000006', toAccount: '1001000011', amount: '1200', description: 'Rent' }),
      }),
    );
    expect(field('Amount')).toHaveValue('');
  });

  it('shows a server error clearly and keeps the form editable', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'INSUFFICIENT_FUNDS', message: 'Insufficient funds. Available balance is 100.00.' }, 409),
    );
    renderAt('/transfer?account=1001000006');

    await userEvent.type(field('To account'), '1001000011');
    await userEvent.type(field('Amount'), '500');
    await submit();

    expect(await screen.findByText('Insufficient funds. Available balance is 100.00.')).toBeInTheDocument();
    expect(screen.queryByText('Transfer successful')).not.toBeInTheDocument();
    expect(field('Amount')).toHaveValue('500');
    expect(field('Amount')).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Transfer' })).toBeEnabled();
  });

  it('maps a server not-found/validation error on the destination to its field', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ code: 'VALIDATION_ERROR', message: 'Source and destination accounts must be different.', field: 'toAccount' }, 400),
    );
    renderAt('/transfer?account=1001000006');

    await userEvent.type(field('To account'), '1001000011');
    await userEvent.type(field('Amount'), '5');
    await submit();

    expect(await screen.findByText('Source and destination accounts must be different.')).toBeInTheDocument();
  });

  it('prevents double submission while a transfer is in flight', async () => {
    let resolve!: (r: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise<Response>((r) => (resolve = r)));
    renderAt('/transfer?account=1001000006');

    await userEvent.type(field('To account'), '1001000011');
    await userEvent.type(field('Amount'), '5{Enter}');

    expect(await screen.findByText('Processing...')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Transfer' })).not.toBeInTheDocument();
    await userEvent.type(field('Amount'), '{Enter}');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolve(jsonResponse(receipt, 201));
    expect(await screen.findByText('Transfer successful')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Transfer' })).toBeEnabled();
  });
});
