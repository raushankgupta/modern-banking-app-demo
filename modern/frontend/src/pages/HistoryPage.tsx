import {
  Button,
  DataTable,
  DataTableSkeleton,
  InlineLoading,
  InlineNotification,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import type { TransactionType } from '../api/dashboard';
import { getHistory, type HistoryEntry } from '../api/transactions';
import { TypeTag } from '../components/TypeTag';
import { formatDateTime, formatMoney } from '../format';
import { checkAccountNumber } from '../validation';

type Filter = 'ALL' | TransactionType;

const filters: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'DEPOSIT', label: 'Deposit' },
  { value: 'WITHDRAWAL', label: 'Withdrawal' },
  { value: 'TRANSFER_DEBIT', label: 'Transfer out' },
  { value: 'TRANSFER_CREDIT', label: 'Transfer in' },
];

const headers = [
  { key: 'date', header: 'Date' },
  { key: 'type', header: 'Type' },
  { key: 'amount', header: 'Amount' },
  { key: 'description', header: 'Description' },
  { key: 'reference', header: 'Reference' },
];

export function HistoryPage() {
  const [params] = useSearchParams();
  const [account, setAccount] = useState(params.get('account') ?? '');
  const [filter, setFilter] = useState<Filter>('ALL');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // The account the table currently shows (the typed value only counts once Search is pressed).
  const [shown, setShown] = useState<string | null>(null);
  const [entries, setEntries] = useState<HistoryEntry[] | null>(null);
  const latest = useRef(0);

  async function load(accountNumber: string, type: Filter) {
    const id = ++latest.current;
    setLoading(true);
    setError(null);
    try {
      const result = await getHistory(accountNumber, type === 'ALL' ? undefined : type);
      if (id !== latest.current) return; // a newer request superseded this one
      setEntries(result);
      setShown(accountNumber);
    } catch (e) {
      if (id !== latest.current) return;
      setEntries(null);
      setShown(null);
      setError(e instanceof ApiError ? e.message : 'Unable to reach the server. Please try again.');
    } finally {
      if (id === latest.current) setLoading(false);
    }
  }

  function search(accountNumber: string, type: Filter): boolean {
    const problem = checkAccountNumber(accountNumber);
    setFieldError(problem);
    if (problem) {
      setEntries(null);
      setShown(null);
      setError(null);
      return false;
    }
    void load(accountNumber.trim(), type);
    return true;
  }

  // Opened from the account details screen with ?account=: load straight away.
  useEffect(() => {
    const initial = params.get('account');
    if (initial) search(initial, 'ALL');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    search(account, filter);
  }

  function handleFilter(value: Filter) {
    setFilter(value);
    if (shown) void load(shown, value);
  }

  const rows = (entries ?? []).map((t) => ({
    id: String(t.transactionId),
    date: formatDateTime(t.transactionDate),
    type: t.transactionType,
    amount: formatMoney(t.amount),
    description: t.description,
    reference: t.referenceNumber,
  }));

  return (
    <>
      <h1 className="page-title">Transaction history</h1>
      <p className="page-subtitle">Newest transactions first</p>

      <form className="history-form" onSubmit={handleSubmit} noValidate>
        <TextInput
          id="history-account"
          labelText="Account number"
          value={account}
          onChange={(e) => setAccount(e.target.value)}
          invalid={!!fieldError}
          invalidText={fieldError ?? ''}
        />
        <Select
          id="history-type"
          labelText="Type"
          value={filter}
          onChange={(e) => handleFilter(e.target.value as Filter)}
        >
          {filters.map((f) => (
            <SelectItem key={f.value} value={f.value} text={f.label} />
          ))}
        </Select>
        {loading ? <InlineLoading description="Loading..." /> : <Button type="submit">Search</Button>}
      </form>

      {error && (
        <InlineNotification kind="error" title="Could not load history" subtitle={error} hideCloseButton lowContrast role="alert" />
      )}

      {loading && !entries && <DataTableSkeleton headers={headers} rowCount={5} showToolbar={false} showHeader={false} />}

      {entries && entries.length === 0 && (
        <InlineNotification
          kind="info"
          title="No transactions"
          subtitle={
            filter === 'ALL'
              ? `Account ${shown} has no transactions yet.`
              : `Account ${shown} has no transactions of this type.`
          }
          hideCloseButton
          lowContrast
        />
      )}

      {entries && entries.length > 0 && (
        <DataTable rows={rows} headers={headers}>
          {({ rows: tableRows, headers: tableHeaders, getTableProps, getHeaderProps, getRowProps }) => (
            <TableContainer title={`Transaction history - account ${shown}`} description={`${entries.length} transaction(s)`}>
              <Table {...getTableProps()} aria-label="Transaction history">
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
                        {row.cells.map((cell) =>
                          cell.info.header === 'type' ? (
                            <TableCell key={cell.id}>
                              <TypeTag type={cell.value as TransactionType} />
                            </TableCell>
                          ) : (
                            <TableCell key={cell.id} className={cell.info.header === 'amount' ? 'cell-right' : undefined}>
                              {cell.value}
                            </TableCell>
                          ),
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </DataTable>
      )}
    </>
  );
}
