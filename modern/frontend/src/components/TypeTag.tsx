import { Tag } from '@carbon/react';
import type { TransactionType } from '../api/dashboard';

const tags: Record<TransactionType, { label: string; color: 'green' | 'red' | 'purple' | 'cyan' }> = {
  DEPOSIT: { label: 'Deposit', color: 'green' },
  WITHDRAWAL: { label: 'Withdrawal', color: 'red' },
  TRANSFER_DEBIT: { label: 'Transfer out', color: 'purple' },
  TRANSFER_CREDIT: { label: 'Transfer in', color: 'cyan' },
};

export function TypeTag({ type }: { type: TransactionType }) {
  const tag = tags[type];
  return (
    <Tag type={tag.color} size="md">
      {tag.label}
    </Tag>
  );
}
