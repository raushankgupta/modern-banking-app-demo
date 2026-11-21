import { request } from './client';

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface AccountSummary {
  accountNumber: string;
  accountType: string;
  status: AccountStatus;
  balance: number;
}

export interface CustomerWithAccounts {
  customerId: number;
  customerName: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  accounts: AccountSummary[];
}

export interface AccountDetails {
  accountNumber: string;
  accountType: string;
  status: AccountStatus;
  balance: number;
  opened: string;
  customerId: number;
  customerName: string;
  email: string | null;
  phone: string | null;
}

export function getCustomerById(customerId: string): Promise<CustomerWithAccounts> {
  return request<CustomerWithAccounts>(`/customers/${encodeURIComponent(customerId)}`);
}

export function getCustomerByAccount(accountNumber: string): Promise<CustomerWithAccounts> {
  return request<CustomerWithAccounts>(`/customers/by-account/${encodeURIComponent(accountNumber)}`);
}

export function getAccount(accountNumber: string): Promise<AccountDetails> {
  return request<AccountDetails>(`/accounts/${encodeURIComponent(accountNumber)}`);
}
