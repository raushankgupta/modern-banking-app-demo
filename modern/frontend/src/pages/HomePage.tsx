// Placeholder: the real dashboard is built in Story F3.
import { Button } from '@carbon/react';
import { useAuth } from '../auth/AuthContext';

export function HomePage() {
  const { session, logout } = useAuth();
  return (
    <main>
      <h1>Modern Banking</h1>
      <p>Signed in as {session?.employeeName}</p>
      <Button kind="tertiary" onClick={logout}>
        Log out
      </Button>
    </main>
  );
}
