import { Tag } from '@carbon/react';
import type { AccountStatus } from '../api/customers';

const colors: Record<AccountStatus, 'green' | 'warm-gray' | 'red'> = {
  ACTIVE: 'green',
  FROZEN: 'warm-gray',
  CLOSED: 'red',
};

const labels: Record<AccountStatus, string> = { ACTIVE: 'Active', FROZEN: 'Frozen', CLOSED: 'Closed' };

export function StatusTag({ status }: { status: AccountStatus }) {
  return (
    <Tag type={colors[status]} size="md">
      {labels[status]}
    </Tag>
  );
}
