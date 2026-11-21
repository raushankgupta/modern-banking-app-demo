import {
  Button,
  InlineLoading,
  InlineNotification,
  Link as CarbonLink,
  RadioButton,
  RadioButtonGroup,
  StructuredListBody,
  StructuredListCell,
  StructuredListRow,
  StructuredListWrapper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
  Tile,
} from '@carbon/react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  getCustomerByAccount,
  getCustomerById,
  type CustomerWithAccounts,
} from '../api/customers';
import { StatusTag } from '../components/StatusTag';
import { formatAccountType, formatMoney } from '../format';

type SearchBy = 'customerId' | 'accountNumber';

const labels: Record<SearchBy, string> = { customerId: 'Customer ID', accountNumber: 'Account number' };

export function CustomerSearchPage() {
  const [searchBy, setSearchBy] = useState<SearchBy>('customerId');
  const [value, setValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [customer, setCustomer] = useState<CustomerWithAccounts | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (loading) return;
    const term = value.trim();
    if (!term) {
      setError('Please enter a search value.');
      setCustomer(null);
      return;
    }
    setLoading(true);
    setError(null);
    setCustomer(null);
    try {
      setCustomer(searchBy === 'customerId' ? await getCustomerById(term) : await getCustomerByAccount(term));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Unable to reach the server. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <h1 className="page-title">Customer search</h1>
      <p className="page-subtitle">Find a customer by ID or by one of their account numbers</p>

      <form className="search-form" onSubmit={handleSubmit} noValidate>
        <RadioButtonGroup
          legendText="Search by"
          name="search-by"
          valueSelected={searchBy}
          onChange={(selected) => {
            setSearchBy(selected as SearchBy);
            setError(null);
          }}
        >
          <RadioButton id="by-customer" labelText="Customer ID" value="customerId" />
          <RadioButton id="by-account" labelText="Account number" value="accountNumber" />
        </RadioButtonGroup>
        <div className="search-row">
          <TextInput
            id="search-value"
            labelText={labels[searchBy]}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            invalid={!!error && !customer}
            invalidText={error ?? ''}
          />
          {loading ? <InlineLoading description="Searching..." /> : <Button type="submit">Search</Button>}
        </div>
      </form>

      {customer && <CustomerResult customer={customer} />}
    </>
  );
}

function CustomerResult({ customer }: { customer: CustomerWithAccounts }) {
  const info: [string, string | number | null][] = [
    ['Customer ID', customer.customerId],
    ['Name', customer.customerName],
    ['Email', customer.email],
    ['Phone', customer.phone],
    ['Address', customer.address],
  ];

  return (
    <div className="search-result">
      <Tile className="customer-card">
        <h2 className="section-title">{customer.customerName}</h2>
        <StructuredListWrapper isCondensed aria-label="Customer information">
          <StructuredListBody>
            {info.map(([label, text]) => (
              <StructuredListRow key={label}>
                <StructuredListCell noWrap>{label}</StructuredListCell>
                <StructuredListCell>{text ?? '-'}</StructuredListCell>
              </StructuredListRow>
            ))}
          </StructuredListBody>
        </StructuredListWrapper>
      </Tile>

      <section aria-labelledby="accounts-heading" className="dashboard-section">
        <h2 id="accounts-heading" className="section-title">
          Accounts
        </h2>
        {customer.accounts.length === 0 ? (
          <InlineNotification
            kind="info"
            title="No accounts"
            subtitle="This customer has no accounts."
            hideCloseButton
            lowContrast
          />
        ) : (
          <TableContainer>
            <Table aria-label="Customer accounts">
              <TableHead>
                <TableRow>
                  <TableHeader>Account number</TableHeader>
                  <TableHeader>Type</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader className="cell-right">Balance</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {customer.accounts.map((account) => (
                  <TableRow key={account.accountNumber}>
                    <TableCell>
                      <CarbonLink as={Link} to={`/accounts/${account.accountNumber}`}>
                        {account.accountNumber}
                      </CarbonLink>
                    </TableCell>
                    <TableCell>{formatAccountType(account.accountType)}</TableCell>
                    <TableCell>
                      <StatusTag status={account.status} />
                    </TableCell>
                    <TableCell className="cell-right">{formatMoney(account.balance)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </section>
    </div>
  );
}
