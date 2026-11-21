import { Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppShell } from './layout/AppShell';
import { screens } from './layout/navItems';
import { AccountDetailsPage } from './pages/AccountDetailsPage';
import { CustomerSearchPage } from './pages/CustomerSearchPage';
import { DashboardPage } from './pages/DashboardPage';
import { DepositPage } from './pages/DepositPage';
import { HistoryPage } from './pages/HistoryPage';
import { LoginPage } from './pages/LoginPage';
import { PlaceholderPage } from './pages/PlaceholderPage';
import { TransferPage } from './pages/TransferPage';
import { WithdrawalPage } from './pages/WithdrawalPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/search" element={<CustomerSearchPage />} />
          <Route path="/accounts/:accountNumber" element={<AccountDetailsPage />} />
          <Route path="/deposit" element={<DepositPage />} />
          <Route path="/withdraw" element={<WithdrawalPage />} />
          <Route path="/transfer" element={<TransferPage />} />
          <Route path="/history" element={<HistoryPage />} />
          {screens
            .filter((screen) => !['/search', '/deposit', '/withdraw', '/transfer', '/history'].includes(screen.to))
            .map((screen) => (
              <Route key={screen.to} path={screen.to} element={<PlaceholderPage title={screen.label} />} />
            ))}
        </Route>
      </Route>
    </Routes>
  );
}
