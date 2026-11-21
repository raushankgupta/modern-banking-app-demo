import { describe, expect, it } from 'vitest';
import { checkAccountNumber, checkAmount } from './validation';

describe('checkAmount', () => {
  it.each(['1', '0.01', '100.5', '100.50', '1,0'.replace(',', '.'), '10000000', '10000000.00', '5.100', ' 25 '])(
    'accepts %s',
    (value) => expect(checkAmount(value)).toBeNull(),
  );

  it.each([
    ['', 'Please enter an amount.'],
    ['   ', 'Please enter an amount.'],
    ['abc', 'Amount must be a valid number.'],
    ['1e3', 'Amount must be a valid number.'],
    ['0', 'Amount must be greater than zero.'],
    ['-5', 'Amount must be greater than zero.'],
    ['1.234', 'Amount cannot have more than two decimal places.'],
    ['10000000.01', 'Amount cannot exceed 10,000,000.00 per transaction.'],
    ['10000001', 'Amount cannot exceed 10,000,000.00 per transaction.'],
  ])('rejects %j', (value, message) => expect(checkAmount(value)).toBe(message));
});

describe('checkAccountNumber', () => {
  it('accepts 1-20 letters and digits', () => {
    expect(checkAccountNumber('1001000001')).toBeNull();
    expect(checkAccountNumber('A'.repeat(20))).toBeNull();
  });

  it('rejects empty, too long and special characters', () => {
    expect(checkAccountNumber('')).toBe('Please enter the account number.');
    expect(checkAccountNumber('A'.repeat(21))).toMatch(/may only contain letters and digits/);
    expect(checkAccountNumber('10-01')).toMatch(/may only contain letters and digits/);
  });
});
