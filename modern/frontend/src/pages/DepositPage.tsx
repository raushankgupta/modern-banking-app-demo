import { deposit } from '../api/transactions';
import { AmountForm } from '../components/AmountForm';

export function DepositPage() {
  return (
    <AmountForm
      title="Cash deposit"
      subtitle="Add funds to an active account"
      submitLabel="Deposit"
      successTitle="Deposit successful"
      submit={deposit}
    />
  );
}
