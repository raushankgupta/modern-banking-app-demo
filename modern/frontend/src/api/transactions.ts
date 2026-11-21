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

export interface TransferReceipt {
  fromAccount: string;
  toAccount: string;
  amount: number;
  fromBalance: number;
  toBalance: number;
  reference: string;
}

export function transfer(
  fromAccount: string,
  toAccount: string,
  amount: string,
  description: string,
): Promise<TransferReceipt> {
  return request<TransferReceipt>('/transfers', {
    method: 'POST',
    body: { fromAccount, toAccount, amount, description: description || null },
  });
}

export interface HistoryEntry {
  transactionId: number;
  transactionType: TransactionType;
  amount: number;
  transactionDate: string;
  referenceNumber: string;
  description: string;
}

/** `type` omitted (or undefined) means all types. */
export function getHistory(accountNumber: string, type?: TransactionType): Promise<HistoryEntry[]> {
  return request<HistoryEntry[]>(`/accounts/${encodeURIComponent(accountNumber)}/transactions`, {
    query: { type },
  });
}
