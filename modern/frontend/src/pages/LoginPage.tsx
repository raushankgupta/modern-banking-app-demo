import { Button, InlineLoading, InlineNotification, PasswordInput, Stack, TextInput } from '@carbon/react';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (session) return <Navigate to={from} replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    if (!username.trim() || !password) {
      setError('Please enter username and password.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await login(username.trim(), password);
      navigate(from, { replace: true });
    } catch (e) {
      setPassword('');
      setError(
        e instanceof ApiError && e.status === 401
          ? 'Invalid username or password.'
          : e instanceof ApiError
            ? e.message
            : 'Unable to reach the server. Please try again.',
      );
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={handleSubmit} noValidate>
        <Stack gap={6}>
          <div>
            <h1 className="login-title">Modern Banking</h1>
            <p className="login-subtitle">Branch operations - sign in to continue</p>
          </div>
          {error && (
            <InlineNotification
              kind="error"
              title="Sign in failed"
              subtitle={error}
              hideCloseButton
              lowContrast
              role="alert"
            />
          )}
          <TextInput
            id="username"
            labelText="Username"
            autoComplete="username"
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <PasswordInput
            id="password"
            labelText="Password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {submitting ? (
            <InlineLoading description="Signing in..." />
          ) : (
            <Button type="submit" className="login-submit">
              Sign in
            </Button>
          )}
        </Stack>
      </form>
    </main>
  );
}
