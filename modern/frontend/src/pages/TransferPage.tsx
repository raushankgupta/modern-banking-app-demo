import { Button, InlineLoading, InlineNotification, TextArea, TextInput } from '@carbon/react';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { transfer, type TransferReceipt } from '../api/transactions';
import { formatMoney } from '../format';
import { checkAccountNumber, checkAmount } from '../validation';

type Field = 'from' | 'to' | 'amount';
type Errors = Partial<Record<Field, string>>;

const serverFields: Record<string, Field> = { fromAccount: 'from', toAccount: 'to', amount: 'amount' };

export function TransferPage() {
  const [params] = useSearchParams();
  const [from, setFrom] = useState(params.get('account') ?? '');
  const [to, setTo] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<TransferReceipt | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setReceipt(null);
    setServerError(null);

    const found: Errors = {};
    const fromError = checkAccountNumber(from, 'source account number');
    const toError = checkAccountNumber(to, 'destination account number');
    const amountError = checkAmount(amount);
    if (fromError) found.from = fromError;
    if (toError) found.to = toError;
    if (!fromError && !toError && from.trim().toLowerCase() === to.trim().toLowerCase()) {
      found.to = 'Source and destination accounts must be different.';
    }
    if (amountError) found.amount = amountError;
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      const result = await transfer(from.trim(), to.trim(), amount.trim(), description.trim());
      setReceipt(result);
      setAmount('');
      setDescription('');
    } catch (e) {
      const field = e instanceof ApiError && e.status === 400 && e.field ? serverFields[e.field] : undefined;
      if (field && e instanceof ApiError) {
        setErrors({ [field]: e.message });
      } else {
        setServerError(e instanceof ApiError ? e.message : 'Unable to reach the server. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1 className="page-title">Fund transfer</h1>
      <p className="page-subtitle">Move funds between two active accounts</p>

      {receipt && (
        <InlineNotification
          kind="success"
          title="Transfer successful"
          subtitle={`Reference ${receipt.reference}. Balance of ${receipt.fromAccount}: ${formatMoney(receipt.fromBalance)}. Balance of ${receipt.toAccount}: ${formatMoney(receipt.toBalance)}`}
          lowContrast
          hideCloseButton
          role="status"
        />
      )}
      {serverError && (
        <InlineNotification kind="error" title="Transfer failed" subtitle={serverError} lowContrast hideCloseButton role="alert" />
      )}

      <form className="amount-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="from-account"
          labelText="From account"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          invalid={!!errors.from}
          invalidText={errors.from}
        />
        <TextInput
          id="to-account"
          labelText="To account"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          invalid={!!errors.to}
          invalidText={errors.to}
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
        {submitting ? <InlineLoading description="Processing..." /> : <Button type="submit">Transfer</Button>}
      </form>
    </>
  );
}
