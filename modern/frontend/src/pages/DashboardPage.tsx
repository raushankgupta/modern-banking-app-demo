import {
  ActionableNotification,
  ClickableTile,
  DataTable,
  DataTableSkeleton,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tile,
} from '@carbon/react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getDashboardSummary, type DashboardSummary, type TransactionType } from '../api/dashboard';
import { TypeTag } from '../components/TypeTag';
import { useAuth } from '../auth/AuthContext';
import { formatDateTime, formatMoney } from '../format';
import { screens } from '../layout/navItems';

const headers = [
  { key: 'date', header: 'Date' },
  { key: 'account', header: 'Account' },
  { key: 'customer', header: 'Customer' },
  { key: 'type', header: 'Type' },
  { key: 'amount', header: 'Amount' },
  { key: 'reference', header: 'Reference' },
];

export function DashboardPage() {
  const { session } = useAuth();
  const navigate = useNavigate();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    setSummary(null);
    getDashboardSummary()
      .then(setSummary)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Unable to load the dashboard.'));
  }, []);

  useEffect(load, [load]);

  const rows = (summary?.recentTransactions ?? []).map((t) => ({
    id: String(t.transactionId),
    date: formatDateTime(t.transactionDate),
    account: t.accountNumber,
    customer: t.customerName,
    type: t.transactionType,
    amount: formatMoney(t.amount),
    reference: t.referenceNumber,
  }));

  return (
    <>
      <h1 className="page-title">Dashboard</h1>
      <p className="page-subtitle">Welcome back, {session?.employeeName}</p>

      {error && (
        <ActionableNotification
          inline
          kind="error"
          title="Could not load the dashboard"
          subtitle={error}
          lowContrast
          hideCloseButton
          role="alert"
          actionButtonLabel="Retry"
          onActionButtonClick={load}
        />
      )}

      <section aria-label="Key figures" className="kpi-grid">
        <KpiTile label="Customers" value={summary && String(summary.customerCount)} loading={!error && !summary} />
        <KpiTile label="Accounts" value={summary && String(summary.accountCount)} loading={!error && !summary} />
        <KpiTile
          label="Total balance (active accounts)"
          value={summary && formatMoney(summary.totalActiveBalance)}
          loading={!error && !summary}
          highlight
        />
      </section>

      <section aria-labelledby="quick-actions-heading" className="dashboard-section">
        <h2 id="quick-actions-heading" className="section-title">
          Quick actions
        </h2>
        <div className="quick-actions">
          {screens.map((screen) => (
            <ClickableTile
              key={screen.to}
              href={screen.to}
              className="quick-action"
              onClick={(e) => {
                e.preventDefault();
                navigate(screen.to);
              }}
            >
              <strong>{screen.label}</strong>
              <span className="quick-action-desc">{screen.description}</span>
            </ClickableTile>
          ))}
        </div>
      </section>

      <section aria-labelledby="recent-heading" className="dashboard-section">
        <h2 id="recent-heading" className="section-title">
          Recent transactions
        </h2>
        {!summary && !error && <DataTableSkeleton headers={headers} rowCount={5} showToolbar={false} showHeader={false} />}
        {summary && rows.length === 0 && <p>No transactions yet.</p>}
        {summary && rows.length > 0 && (
          <DataTable rows={rows} headers={headers}>
            {({ rows: tableRows, headers: tableHeaders, getTableProps, getHeaderProps, getRowProps }) => (
              <TableContainer>
                <Table {...getTableProps()} aria-label="Recent transactions">
                  <TableHead>
                    <TableRow>
                      {tableHeaders.map((header) => {
                        const { key, ...headerProps } = getHeaderProps({ header });
                        return (
                          <TableHeader key={key} {...headerProps} className={header.key === 'amount' ? 'cell-right' : undefined}>
                            {header.header}
                          </TableHeader>
                        );
                      })}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {tableRows.map((row) => {
                      const { key, ...rowProps } = getRowProps({ row });
                      return (
                        <TableRow key={key} {...rowProps}>
                          {row.cells.map((cell) => {
                            if (cell.info.header === 'type') {
                              return (
                                <TableCell key={cell.id}>
                                  <TypeTag type={cell.value as TransactionType} />
                                </TableCell>
                              );
                            }
                            return (
                              <TableCell key={cell.id} className={cell.info.header === 'amount' ? 'cell-right' : undefined}>
                                {cell.value}
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DataTable>
        )}
      </section>
    </>
  );
}

function KpiTile({
  label,
  value,
  loading,
  highlight,
}: {
  label: string;
  value: string | null | undefined;
  loading: boolean;
  highlight?: boolean;
}) {
  return (
    <Tile className={highlight ? 'kpi-tile kpi-tile--highlight' : 'kpi-tile'}>
      <div className="kpi-label">{label}</div>
      {loading ? <SkeletonText heading width="60%" /> : <div className="kpi-value">{value ?? '-'}</div>}
    </Tile>
  );
}
