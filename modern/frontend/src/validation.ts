// Client-side checks that mirror the backend BankValidator (the server remains the source of truth).
export const MAX_AMOUNT = 10_000_000;

const ACCOUNT_NUMBER = /^[A-Za-z0-9]{1,20}$/;

/** Returns an error message, or null when the account number is acceptable. */
export function checkAccountNumber(text: string, label = 'account number'): string | null {
  const value = text.trim();
  if (!value) return `Please enter the ${label}.`;
  if (!ACCOUNT_NUMBER.test(value)) return `The ${label} may only contain letters and digits (max 20).`;
  return null;
}

/** Returns an error message, or null when the amount is acceptable. */
export function checkAmount(text: string): string | null {
  const value = text.trim();
  if (!value) return 'Please enter an amount.';
  if (!/^[+-]?(\d+(\.\d*)?|\.\d+)$/.test(value)) return 'Amount must be a valid number.';
  const number = Number(value);
  if (!(number > 0)) return 'Amount must be greater than zero.';
  const decimals = value.split('.')[1]?.replace(/0+$/, '') ?? '';
  if (decimals.length > 2) return 'Amount cannot have more than two decimal places.';
  if (number > MAX_AMOUNT) return 'Amount cannot exceed 10,000,000.00 per transaction.';
  return null;
}
