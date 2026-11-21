import { request } from './client';

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_DEBIT' | 'TRANSFER_CREDIT';

export interface RecentTransaction {
  transactionId: number;
  accountNumber: string;
  customerName: string;
  transactionType: TransactionType;
  amount: number;
  transactionDate: string;
  referenceNumber: string;
  description: string;
}

export interface DashboardSummary {
  customerCount: number;
  accountCount: number;
  totalActiveBalance: number;
  recentTransactions: RecentTransaction[];
}

export function getDashboardSummary(): Promise<DashboardSummary> {
  return request<DashboardSummary>('/dashboard/summary');
}
