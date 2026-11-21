# Modern Banking

Web modernization of the legacy Swing banking app: a **React + TypeScript** frontend (Carbon Design System), a
**Spring Boot** REST API (Java 21) and a **SQLite** database. The legacy source and `legacybank.db` in the
repository root are never modified; everything new lives under `modern/`.

| Part | Folder | Stack |
|------|--------|-------|
| Backend | [`backend/`](backend) | Spring Boot 3.3, Spring JDBC (`JdbcClient`, `BigDecimal`), JWT (HS256) + BCrypt, sqlite-jdbc |
| Frontend | [`frontend/`](frontend) | Vite, React 19, TypeScript, `@carbon/react`, React Router, Vitest + Testing Library |
| Database | [`database/`](database) | SQLite schema + seed, `init.sh` (build/reset), `verify.sh` (evidence checks) |

## Prerequisites

- Java 21 and Maven 3.9+
- Node.js 22+ and npm
- `sqlite3` and `htpasswd` (Apache utils; preinstalled on macOS) - used by `database/init.sh`

## Run locally

All commands are run from the repository root. Use three terminals (or run the first two once).

1. **Create the modern database** (a separate file; the legacy `legacybank.db` is never touched):

   ```bash
   modern/database/init.sh
   ```

   This builds `modern/database/data/modernbank.db` from `schema.sql` and `seed.sql` (10 customers, 15 accounts)
   and generates the BCrypt hash for the demo user at run time. Re-running resets the data to the seed state.

2. **Start the backend** (http://localhost:8080):

   ```bash
   cd modern/backend && mvn spring-boot:run
   ```

3. **Start the frontend** (http://localhost:5173). Vite proxies `/api` to the backend, so no CORS setup is needed:

   ```bash
   cd modern/frontend && npm install && npm run dev
   ```

### Demo credentials

| Username | Password |
|----------|----------|
| `admin` | `admin123` |

Seed accounts are `1001000001` - `1001000015`. `1001000008` is FROZEN and `1001000013` is CLOSED, which is handy for
showing the "account is not active" rule.

### Demo walkthrough

Sign in, then: dashboard (KPIs, quick actions, recent transactions) -> customer search (customer ID `1004` or
account `1001000006`) -> account details -> deposit -> withdrawal -> transfer -> transaction history.

## Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `BANK_DB_PATH` | `../database/data/modernbank.db` | SQLite file used by the backend. It refuses to open `legacybank.db`. |
| `BANK_JWT_SECRET` | dev-only value | HS256 signing key (at least 32 bytes). **Set it for anything beyond a local demo.** |
| `BANK_JWT_EXPIRY_MINUTES` | `60` | Token lifetime |
| `BANK_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origins allowed to call the API directly |
| `SERVER_PORT` | `8080` | Backend port |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | Backend address used by the Vite dev proxy |
| `VITE_API_BASE_URL` | empty (same origin) | Base URL for API calls in a production build |
| `ADMIN_PASSWORD` | `admin123` | Password `init.sh` hashes for the seeded admin user |

## API overview

All endpoints are under `/api/v1` and, except login and health, need `Authorization: Bearer <token>`.

| Method and path | Purpose |
|-----------------|---------|
| `POST /auth/login`, `GET /auth/me` | Sign in; current employee |
| `GET /customers/{id}`, `GET /customers/by-account/{accountNumber}` | Customer with accounts |
| `GET /accounts/{accountNumber}` | Account details with owner |
| `POST /accounts/{accountNumber}/deposits`, `.../withdrawals` | Deposit / withdraw |
| `POST /transfers` | Atomic transfer between two accounts (one shared `TRF` reference) |
| `GET /accounts/{accountNumber}/transactions?type=` | History, newest first, optional type filter |
| `GET /dashboard/summary` | Counts, total active balance, recent transactions |
| `GET /actuator/health` (no auth) | Health check |

Errors share one shape: `{ "code": "...", "message": "...", "field": "..." | null }`.

## Tests and checks

```bash
cd modern/backend && mvn test          # backend: 126 tests (each class builds its own temp database)
cd modern/frontend && npm test         # frontend: 66 tests
cd modern/frontend && npm run build    # type-check + production build

# Evidence that legacy files are unchanged and the modern DB enforces its rules (read-only):
LEGACY_DB_BASELINE=/path/to/legacy-db.sha256 modern/database/verify.sh
```

`LEGACY_DB_BASELINE` is optional: a `shasum -a 256 legacybank.db` file recorded **before** any work and kept outside the
repository. Without it the `legacybank.db` hash check is reported as SKIP. The script exits non-zero if anything fails.

## Business rules carried over from the legacy app

- Amount must be greater than 0, have at most 2 decimals and not exceed 10,000,000.00.
- Account numbers are 1-20 letters or digits.
- Deposits, withdrawals and both legs of a transfer need an ACTIVE account. There is no overdraft.
- A transfer needs two different accounts and commits atomically with one shared `TRF` reference.
- Reference numbers are `DEP` / `WDR` / `TRF` plus 10 digits from `REFERENCE_SEQUENCE`.
- History is newest first, with an optional type filter.

## Running without Docker

This project is run directly with `mvn spring-boot:run` and `npm run dev` (see **Run locally** above). Docker images
and Docker Compose are intentionally not part of this modernization.
