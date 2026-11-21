import {
  Breadcrumb,
  BreadcrumbItem,
  Button,
  InlineNotification,
  SkeletonText,
  StructuredListBody,
  StructuredListCell,
  StructuredListRow,
  StructuredListWrapper,
  Tile,
} from '@carbon/react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getAccount, type AccountDetails } from '../api/customers';
import { StatusTag } from '../components/StatusTag';
import { formatAccountType, formatDate, formatMoney } from '../format';

export function AccountDetailsPage() {
  const { accountNumber = '' } = useParams();
  const navigate = useNavigate();
  const [account, setAccount] = useState<AccountDetails | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setAccount(null);
    setError(null);
    getAccount(accountNumber)
      .then(setAccount)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Unable to reach the server.'));
  }, [accountNumber]);

  const go = (path: string) => navigate(`${path}?account=${encodeURIComponent(accountNumber)}`);

  return (
    <>
      <Breadcrumb noTrailingSlash>
        <BreadcrumbItem>
          <Link to="/search">Customer search</Link>
        </BreadcrumbItem>
        <BreadcrumbItem isCurrentPage>Account {accountNumber}</BreadcrumbItem>
      </Breadcrumb>
      <h1 className="page-title">Account details</h1>

      {error && (
        <InlineNotification kind="error" title="Account not found" subtitle={error} hideCloseButton lowContrast role="alert" />
      )}
      {!account && !error && <SkeletonText paragraph lineCount={5} />}

      {account && (
        <>
          <Tile className="customer-card">
            <div className="kpi-label">Balance</div>
            <div className="kpi-value">{formatMoney(account.balance)}</div>
            <StructuredListWrapper isCondensed aria-label="Account information">
              <StructuredListBody>
                <Row label="Account number" value={account.accountNumber} />
                <Row label="Type" value={formatAccountType(account.accountType)} />
                <Row label="Status" value={<StatusTag status={account.status} />} />
                <Row label="Opened" value={formatDate(account.opened)} />
                <Row label="Owner" value={account.customerName} />
                <Row label="Customer ID" value={String(account.customerId)} />
                <Row label="Email" value={account.email ?? '-'} />
                <Row label="Phone" value={account.phone ?? '-'} />
              </StructuredListBody>
            </StructuredListWrapper>
          </Tile>
          <div className="account-actions">
            <Button onClick={() => go('/deposit')}>Deposit</Button>
            <Button kind="tertiary" onClick={() => go('/withdraw')}>
              Withdraw
            </Button>
            <Button kind="tertiary" onClick={() => go('/history')}>
              History
            </Button>
          </div>
        </>
      )}
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <StructuredListRow>
      <StructuredListCell noWrap>{label}</StructuredListCell>
      <StructuredListCell>{value}</StructuredListCell>
    </StructuredListRow>
  );
}
