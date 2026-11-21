import type { DashboardSummary } from '../api/dashboard';

export const summaryFixture: DashboardSummary = {
  customerCount: 10,
  accountCount: 15,
  totalActiveBalance: 303304,
  recentTransactions: [
    {
      transactionId: 90010,
      accountNumber: '1001000006',
      customerName: 'Priya Sharma',
      transactionType: 'TRANSFER_DEBIT',
      amount: 1200,
      transactionDate: '2026-11-20T10:15:00',
      referenceNumber: 'TRF0000000006',
      description: 'Transfer to 1001000011',
    },
    {
      transactionId: 90002,
      accountNumber: '1001000001',
      customerName: 'Arjun Mehta',
      transactionType: 'WITHDRAWAL',
      amount: 750,
      transactionDate: '2026-11-19T09:00:00',
      referenceNumber: 'WDR0000000002',
      description: 'ATM-style cash withdrawal',
    },
    {
      transactionId: 90001,
      accountNumber: '1001000001',
      customerName: 'Arjun Mehta',
      transactionType: 'DEPOSIT',
      amount: 5000,
      transactionDate: '2026-11-18T08:30:00',
      referenceNumber: 'DEP0000000001',
      description: 'Cash deposit',
    },
  ],
};

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

export const customerFixture = {
  customerId: 3,
  customerName: 'Priya Sharma',
  email: 'priya@example.com',
  phone: '555-0103',
  address: '12 Park Street',
  accounts: [
    { accountNumber: '1001000006', accountType: 'SAVINGS', status: 'ACTIVE', balance: 15000.5 },
    { accountNumber: '1001000008', accountType: 'FIXED_DEPOSIT', status: 'FROZEN', balance: 2000 },
  ],
};

export const accountFixture = {
  accountNumber: '1001000006',
  accountType: 'SAVINGS',
  status: 'ACTIVE',
  balance: 15000.5,
  opened: '2024-03-15',
  customerId: 3,
  customerName: 'Priya Sharma',
  email: 'priya@example.com',
  phone: '555-0103',
};
