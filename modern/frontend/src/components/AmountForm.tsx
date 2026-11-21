import { Button, InlineLoading, InlineNotification, TextArea, TextInput } from '@carbon/react';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import type { TransactionReceipt } from '../api/transactions';
import { formatMoney } from '../format';
import { checkAccountNumber, checkAmount } from '../validation';

interface Props {
  title: string;
  subtitle: string;
  submitLabel: string;
  successTitle: string;
  submit: (accountNumber: string, amount: string, description: string) => Promise<TransactionReceipt>;
}

type Errors = Partial<Record<'account' | 'amount', string>>;

/** Shared form for the cash deposit and cash withdrawal screens. */
export function AmountForm({ title, subtitle, submitLabel, successTitle, submit }: Props) {
  const [params] = useSearchParams();
  const [account, setAccount] = useState(params.get('account') ?? '');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<TransactionReceipt | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setReceipt(null);
    setServerError(null);

    const found: Errors = {};
    const accountError = checkAccountNumber(account);
    const amountError = checkAmount(amount);
    if (accountError) found.account = accountError;
    if (amountError) found.amount = amountError;
    setErrors(found);
    if (accountError || amountError) return;

    setSubmitting(true);
    try {
      const result = await submit(account.trim(), amount.trim(), description.trim());
      setReceipt(result);
      setAmount('');
      setDescription('');
    } catch (e) {
      if (e instanceof ApiError && e.status === 400 && (e.field === 'amount' || e.field === 'accountNumber')) {
        setErrors({ [e.field === 'amount' ? 'amount' : 'account']: e.message });
      } else {
        setServerError(e instanceof ApiError ? e.message : 'Unable to reach the server. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1 className="page-title">{title}</h1>
      <p className="page-subtitle">{subtitle}</p>

      {receipt && (
        <InlineNotification
          kind="success"
          title={successTitle}
          subtitle={`Reference ${receipt.reference}. New balance: ${formatMoney(receipt.balance)}`}
          lowContrast
          hideCloseButton
          role="status"
        />
      )}
      {serverError && (
        <InlineNotification kind="error" title="Transaction failed" subtitle={serverError} lowContrast hideCloseButton role="alert" />
      )}

      <form className="amount-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="account"
          labelText="Account number"
          value={account}
          onChange={(e) => setAccount(e.target.value)}
          invalid={!!errors.account}
          invalidText={errors.account}
        />
        <TextInput
          id="amount"
          labelText="Amount"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          invalid={!!errors.amount}
          invalidText={errors.amount}
        />
        <TextArea
          id="description"
          labelText="Description (optional)"
          rows={2}
          maxCount={200}
          enableCounter
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        {submitting ? <InlineLoading description="Processing..." /> : <Button type="submit">{submitLabel}</Button>}
      </form>
    </>
  );
}
