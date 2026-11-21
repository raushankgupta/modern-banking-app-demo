import { withdraw } from '../api/transactions';
import { AmountForm } from '../components/AmountForm';

export function WithdrawalPage() {
  return (
    <AmountForm
      title="Cash withdrawal"
      subtitle="Take funds from an active account"
      submitLabel="Withdraw"
      successTitle="Withdrawal successful"
      submit={withdraw}
    />
  );
}
