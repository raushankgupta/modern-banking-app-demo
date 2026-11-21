import { request } from './client';
import type { TransactionType } from './dashboard';

export interface TransactionReceipt {
  accountNumber: string;
  transactionType: TransactionType;
  amount: number;
  balance: number;
  reference: string;
}

/** Amount is sent as text so the server validates it exactly like the legacy text field. */
export function deposit(accountNumber: string, amount: string, description: string): Promise<TransactionReceipt> {
  return request<TransactionReceipt>(`/accounts/${encodeURIComponent(accountNumber)}/deposits`, {
    method: 'POST',
    body: { amount, description: description || null },
  });
}

export function withdraw(accountNumber: string, amount: string, description: string): Promise<TransactionReceipt> {
  return request<TransactionReceipt>(`/accounts/${encodeURIComponent(accountNumber)}/withdrawals`, {
    method: 'POST',
    body: { amount, description: description || null },
  });
}
