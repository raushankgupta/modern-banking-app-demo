// Legacy shows balances as "#,##0.00" with no currency symbol; keep that single formatting point here.
const money = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' });

export function formatMoney(value: number): string {
  return money.format(value);
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

export function formatDate(iso: string): string {
  return dateOnly.format(new Date(iso));
}

/** Turns SAVINGS / FIXED_DEPOSIT into "Savings" / "Fixed deposit". */
export function formatAccountType(type: string): string {
  const text = type.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
