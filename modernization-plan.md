# Legacy Banking App Modernization Plan

## Top-Level Overview

**Goal:** Replace the legacy Java Swing + JDBC + SQLite desktop app with a web app: React + TypeScript frontend and Java 21 + Spring Boot backend. The data stays in SQLite (decision: keep SQLite, no migration).

**Hard constraints**
- Legacy source (`src/`, `pom.xml`, `database/`, `run.*`) and the legacy `legacybank.db` stay strictly read-only.
- All new code goes under `modern/`.
- All GitHub Issues, branches, commits and PRs go to `origin` = `https://github.com/raushankgupta/modern-banking-app-demo.git`. Nothing goes to the facilitator's repository.
- Do not commit `target/`, `node_modules/` or any `*.db` file.
- Plan mode cannot create Issues. They are created with `gh` in Agent mode after approval.

**Decisions confirmed by the user**

| Topic | Decision |
|-------|----------|
| Database | Keep SQLite. The modern app uses its own DB file, built from copies of the legacy schema and seed scripts. No migration. |
| Data access | Spring JDBC (`JdbcClient`) with `BigDecimal` |
| Authentication | JWT + BCrypt. Hashing applies to the modern DB only. Demo login stays `admin` / `admin123`. |
| UI library | Carbon Design System (`@carbon/react`) |
| Epics | Four Epics: Backend, Frontend, Database, DevOps |
| GitHub linking | Epic = Issue with label `epic` and the exact component title. Each Story is a sub-issue of its own component Epic only, with a task-list fallback. |

Items I propose without asking. Say so if you disagree:
- Feature branch name: `feature/modern-banking-app`.
- Money stays `NUMERIC(15,2)` in the DB, as in legacy. The backend converts it to `BigDecimal` with scale 2 and `HALF_UP`. Storing integer cents is out of scope because it would change the schema.
- Frontend tests use Vitest + React Testing Library. Backend tests use JUnit 5 with a temporary SQLite file.

## Legacy Analysis Summary (evidence)

**Architecture:** Swing desktop app. `BankingApplication.main` runs `DatabaseInitializer.initializeIfNeeded()` and then `LoginFrame`. Each frame holds its own SQL and business logic. There is no service or DAO layer. The only runtime dependency is `sqlite-jdbc 3.45.3.0`. The build targets Java 8 and produces a shaded jar.

**Screens:** Login, Main (dashboard menu), CustomerSearch (by customer ID or account number), AccountDetails, Deposit, Withdrawal, Transfer, TransactionHistory.

**Business rules to preserve**

| Rule | Source |
|------|--------|
| Amount > 0, at most 2 decimals, maximum 10,000,000.00 | [`BankUtil.java`](src/main/java/com/legacybank/BankUtil.java) |
| Account number: 1-20 alphanumeric characters | [`BankUtil.java`](src/main/java/com/legacybank/BankUtil.java) |
| Deposit, withdrawal and both transfer legs require account `STATUS = 'ACTIVE'` | [`DepositFrame.java`](src/main/java/com/legacybank/DepositFrame.java), [`WithdrawalFrame.java`](src/main/java/com/legacybank/WithdrawalFrame.java), [`TransferFrame.java`](src/main/java/com/legacybank/TransferFrame.java) |
| No overdraft. The balance must be at least the amount. The DB also has `CHECK (BALANCE >= 0)`. | [`sqlite-schema.sql`](database/sqlite-schema.sql) |
| Transfer source and destination must differ. Debit, credit and both transaction rows commit atomically with one shared reference number. | [`TransferFrame.java`](src/main/java/com/legacybank/TransferFrame.java) |
| Reference format: prefix `DEP`/`WDR`/`TRF` + sequence from the `REFERENCE_SEQUENCE` table (`last_insert_rowid()`) | [`BankUtil.java`](src/main/java/com/legacybank/BankUtil.java) |
| Transaction types: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`. History is newest first, with an optional type filter. | [`TransactionHistoryFrame.java`](src/main/java/com/legacybank/TransactionHistoryFrame.java) |
| Login: employee with status `ACTIVE`, plaintext password. No roles. | [`LoginFrame.java`](src/main/java/com/legacybank/LoginFrame.java), [`AppSession.java`](src/main/java/com/legacybank/AppSession.java) |
| Connection: `foreign_keys=ON`, busy timeout 10 s, `BEGIN IMMEDIATE` transactions | [`DBConnection.java`](src/main/java/com/legacybank/DBConnection.java) |

**Schema** ([`sqlite-schema.sql`](database/sqlite-schema.sql)): `EMPLOYEES`, `CUSTOMERS`, `ACCOUNTS` (types `SAVINGS`/`CHECKING`/`FIXED_DEPOSIT`, statuses `ACTIVE`/`FROZEN`/`CLOSED`), `TRANSACTIONS`, `REFERENCE_SEQUENCE`. Indexes: `IDX_ACCOUNTS_CUSTOMER`, `IDX_TXN_ACCOUNT_NUMBER`, `IDX_TXN_REFERENCE`. Seed data ([`seed-data.sql`](database/seed-data.sql)): 1 employee, 10 customers, 15 accounts, 7 transactions.

**Legacy risks the modernization addresses:** plaintext passwords, business logic in UI handlers, no service layer, static session, string-based dates, no audit trail. The app writes `legacybank.db` at startup, so the modern app must never point at that file.

## Target Architecture

```
modern/
  backend/            Spring Boot 3, Java 21, Spring Security (JWT), Spring JDBC, sqlite-jdbc
  frontend/           React + TypeScript (Vite), @carbon/react, React Router
  database/           schema.sql, seed.sql (copies of legacy plus BCrypt hash), init script, integrity checks
  docker-compose.yml  backend + frontend, SQLite file on a named volume
  README.md
```

Backend layers: Controller (REST `/api/v1/...`) -> Service (business rules) -> Repository (`JdbcClient`) -> SQLite. Each money operation runs in one `@Transactional` unit.

REST surface (proposed):
- `POST /api/v1/auth/login`
- `GET /api/v1/customers/{id}`
- `GET /api/v1/accounts/{accountNumber}`
- `POST /api/v1/accounts/{accountNumber}/deposits`
- `POST /api/v1/accounts/{accountNumber}/withdrawals`
- `POST /api/v1/transfers`
- `GET /api/v1/accounts/{accountNumber}/transactions?type=`
- `GET /api/v1/dashboard/summary`

**Dashboard UI enhancement (simple, demo-focused):** a Carbon UI shell (Header + SideNav) and a dashboard with KPI tiles (customers, accounts, total balance), a Quick Actions panel (Deposit, Withdraw, Transfer, Search) and a Recent Transactions table with colored Tags per transaction type. No custom theming beyond the Carbon `g100`/`white` theme.

## Implementation Sequence

1. Setup (S0): GitHub labels, 4 Epics and all Stories, feature branch, `modern/` skeleton.
2. Database: D1, D2 (the backend needs the DB file).
3. Backend foundation: B1, B2 (rules and error model), B3 (auth).
4. Backend features: B4, then B5, B6, B7, B8, then B9.
5. Frontend foundation: F1, F2, F3 (dashboard). F3 needs B9.
6. Frontend features: F4, F5, F6, F7.
7. DevOps: O1, O2, O3, O4.
8. Database D3 and final validation (Stage 3): legacy-unchanged check, full test run, Docker run, PR to `main`.

Each Story is implemented, tested, committed, pushed and commented on its Issue before the next one starts.

---

## Sub-Tasks

### S0 - GitHub setup (no code)
- **Intent:** Create the tracking structure in the user's repository.
- **Expected outcomes:** Labels, 4 Epics and all Stories exist as Issues in `origin`. Each Story is linked as a sub-issue to its component Epic only. The feature branch exists.
- **Todo:**
  1. Run `git remote -v` and confirm that `origin` is `raushankgupta/modern-banking-app-demo`.
  2. Create labels: `epic`, `story`, `backend`, `frontend`, `database`, `devops`.
  3. Create the Epics titled exactly `Backend`, `Frontend`, `Database`, `DevOps`, each with label `epic` and a short goal.
  4. Create each Story below with its acceptance criteria and label `story` plus its component label. Link it as a sub-issue to its component Epic. Fall back to a task list in the Epic body plus `Parent: #n` if sub-issues are unavailable.
  5. Create `feature/modern-banking-app` from `main` and push it to `origin`.
- **Relevant context:** [`legacy_banking_modernization_workshop.md`](legacy_banking_modernization_workshop.md) section 3 and the guardrails.
- **Status:** [x] done
- **Result:** Epics #2 Backend, #3 Frontend, #4 Database, #5 DevOps. Stories: Database #6-#8 (D1-D3), Backend #9-#17 (B1-B9), Frontend #18-#24 (F1-F7), DevOps #25-#28 (O1-O4). Each Story is a sub-issue of its own Epic only. Branch `feature/modern-banking-app` is pushed to `origin`. The legacy DB baseline SHA-256 is saved in `/tmp/bobwork/legacy-db.sha256` (outside the repo).


---

## Epic: Database
Goal: provide the modern SQLite database with the same schema and rules as legacy, without touching legacy files.

### D1 - Create modern schema and seed scripts
- **Intent:** Own copies of the schema and seed data under `modern/database/`. Legacy files are copied, not edited.
- **Todo:**
  1. Copy [`sqlite-schema.sql`](database/sqlite-schema.sql) to `modern/database/schema.sql` unchanged.
  2. Copy [`seed-data.sql`](database/seed-data.sql) to `modern/database/seed.sql`. Replace only the `admin` password with the placeholder `__ADMIN_PASSWORD_BCRYPT__` (a committed hash is blocked by the Block Secrets hook; decision: the D2 init script generates the hash).
  3. Document that `PASSWORD VARCHAR(100)` fits a 60-character BCrypt hash.
- **Acceptance criteria:**
  - [x] `schema.sql` defines the same 5 tables, constraints and 3 indexes as legacy.
  - [x] `seed.sql` loads 1 employee, 10 customers, 15 accounts and 7 transactions, identical to legacy except for the admin password placeholder.
  - [x] `seed.sql` contains no plaintext password and no committed hash; the BCrypt hash is generated by D2 at DB creation time.
  - [x] No file under `src/`, `database/` or `legacybank.db` is modified.
- **Relevant context:** [`database/README.md`](database/README.md).
- **Status:** [x] done (commit 64eb662, issue #6)

### D2 - Add database init/reset script
- **Intent:** Build a fresh modern SQLite file reproducibly.
- **Todo:**
  1. Add a script (`modern/database/init.sh`, with a `.bat` equivalent only if requested) that creates `modern/database/data/modernbank.db` from `schema.sql` and `seed.sql`. It generates a BCrypt hash of the demo password `admin123` at run time and substitutes `__ADMIN_PASSWORD_BCRYPT__`. Nothing hash-like is committed.
  2. Refuse to run if the target path resolves to the legacy `legacybank.db`.
  3. Keep `.db` files out of git. Writing `modern/.gitignore` was blocked by an ignore rule in this environment, so the ignore file is deferred to O4 (user to decide); D2 verifies with `git ls-files` that no `.db` is tracked.
- **Acceptance criteria:**
  - [x] Running the script on a clean checkout creates a DB with the expected row counts (1/10/15/7).
  - [x] The seeded DB stores a 60-character BCrypt hash for `admin`, not plaintext.
  - [x] Re-running it resets the DB to the seed state.
  - [x] The script exits with an error if pointed at the legacy `legacybank.db`.
  - [x] No `.db` file is tracked by git.
- **Status:** [x] done (issue #7)

### D3 - Add legacy-immutability and integrity check
- **Intent:** Provide the evidence that legacy assets are unchanged and that the modern DB keeps its integrity rules.
- **Todo:**
  1. Add a script that fails if `git status` shows changes under `src/`, `database/`, `pom.xml` or `run.*`.
  2. The script also compares the legacy `legacybank.db` hash against a baseline hash recorded before work starts, outside the repo.
  3. Add SQL checks on the modern DB: `CHECK` constraints, FKs and unique constraints reject bad data.
- **Acceptance criteria:**
  - [ ] The script passes on an untouched legacy tree and fails after a simulated edit.
  - [ ] Negative balance, an invalid status or type, a duplicate account number and an orphan transaction are each rejected by the modern DB.
  - [ ] The script output is usable in the final validation summary.
- **Status:** [ ] pending

---

## Epic: Backend
Goal: a Spring Boot REST API that reproduces all legacy banking rules in a testable service layer.

### B1 - Scaffold Spring Boot project and SQLite connectivity
- **Intent:** A runnable, configurable backend.
- **Todo:**
  1. Create `modern/backend` (Maven, Java 21, Spring Boot 3: web, security, jdbc, validation, test; `sqlite-jdbc`).
  2. Configure the DB path through a property or env var (default `../database/data/modernbank.db`), and apply `foreign_keys=ON` and a 10 s busy timeout.
  3. Add CORS for the Vite dev origin, and a health endpoint.
- **Acceptance criteria:**
  - [x] `mvn test` passes and `mvn spring-boot:run` starts on a configured port.
  - [x] The health endpoint returns UP when the DB is reachable.
  - [x] Foreign keys are enforced on connections (tested).
  - [x] Startup fails with a clear message if the DB path points to the legacy `legacybank.db`.
- **Relevant context:** [`DBConnection.java`](src/main/java/com/legacybank/DBConnection.java), [`db.properties`](src/main/resources/db.properties).
- **Status:** [x] done (issue #9)

### B2 - Implement validation rules, reference numbers and error model
- **Intent:** Port `BankUtil` rules into one shared, tested component.
- **Todo:**
  1. Amount parsing: `BigDecimal`, > 0, at most 2 decimals, maximum 10,000,000.00.
  2. Account number check: 1-20 alphanumeric characters.
  3. Reference generation through `REFERENCE_SEQUENCE`, with prefixes `DEP`/`WDR`/`TRF` in the legacy format.
  4. A uniform JSON error response (code, message, field) through `@ControllerAdvice`.
- **Acceptance criteria:**
  - [x] Tests cover the legacy `BankUtilTest` cases: valid 100, 12.5 and 0.01; rejected 0, negative, non-numeric, 3 decimals, null, empty and amounts over the maximum.
  - [x] Reference numbers are unique, increasing and match the legacy format.
  - [x] Validation errors return HTTP 400 with the uniform error body.
- **Relevant context:** [`BankUtil.java`](src/main/java/com/legacybank/BankUtil.java), [`BankUtilTest.java`](src/test/java/com/legacybank/BankUtilTest.java), [`DatabaseIntegrationTest.java`](src/test/java/com/legacybank/DatabaseIntegrationTest.java).
- **Status:** [x] done (issue #10)

### B3 - Implement JWT authentication
- **Intent:** Secure login replacing the plaintext legacy login.
- **Todo:**
  1. `POST /api/v1/auth/login` checks `USERNAME`, the BCrypt hash and `STATUS = 'ACTIVE'`, and returns a JWT with the employee name.
  2. Protect all other `/api/v1/**` endpoints with a Spring Security JWT filter.
  3. Read the secret and expiry from configuration, not code.
- **Acceptance criteria:**
  - [x] `admin` / `admin123` returns 200 and a token.
  - [x] A wrong password, an unknown user or an `INACTIVE` employee returns 401 with a generic message.
  - [x] Protected endpoints return 401 without a token and 200 with a valid one.
  - [x] An expired token is rejected.
  - [x] No plaintext password is logged or returned.
- **Relevant context:** [`LoginFrame.java`](src/main/java/com/legacybank/LoginFrame.java), [`AppSession.java`](src/main/java/com/legacybank/AppSession.java).
- **Status:** [x] done (issue #11). Approach: Spring Security OAuth2 Resource Server (Nimbus, HS256), 60 min tokens, secret from `BANK_JWT_SECRET` with a dev-only default.

### B4 - Customer and account lookup API
- **Intent:** Back the CustomerSearch and AccountDetails screens.
- **Todo:**
  1. `GET /customers/{id}` returns the customer and all their accounts.
  2. `GET /accounts/{accountNumber}` returns the account and its owner. Balances are `BigDecimal` with scale 2.
  3. Search by account number also returns the owning customer and accounts, as in the legacy search.
- **Acceptance criteria:**
  - [x] A seeded customer and a seeded account number return the expected data and balances.
  - [x] An unknown ID or account number returns 404.
  - [x] An invalid account number format returns 400.
  - [x] FROZEN and CLOSED accounts are returned with their status.
- **Relevant context:** [`CustomerSearchFrame.java`](src/main/java/com/legacybank/CustomerSearchFrame.java), [`AccountDetailsFrame.java`](src/main/java/com/legacybank/AccountDetailsFrame.java).
- **Status:** [x] done (issue #12). Endpoints: `GET /api/v1/customers/{id}`, `GET /api/v1/customers/by-account/{accountNumber}`, `GET /api/v1/accounts/{accountNumber}`.

### B5 - Deposit API
- **Todo:** `POST /accounts/{accountNumber}/deposits` with `amount` and an optional `description`. Balance update plus a `DEPOSIT` transaction row with a `DEP` reference, in one DB transaction.
- **Acceptance criteria:**
  - [x] A valid deposit increases the balance and records one `DEPOSIT` transaction. The response contains the reference number and the new balance.
  - [x] A non-ACTIVE account returns 409 and nothing changes.
  - [x] An invalid amount returns 400. An unknown account returns 404.
  - [x] If the transaction insert fails, the balance is rolled back (tested).
- **Relevant context:** [`DepositFrame.java`](src/main/java/com/legacybank/DepositFrame.java).
- **Status:** [x] done (issue #13). Approach (user-approved): mirror legacy read-check-write inside one BEGIN IMMEDIATE transaction. Shared building blocks for B6/B7: `LedgerRepository`, `AccountRules`, `BusinessRuleException` (409), `TransactionReceipt`, `AmountRequest`, `TestDatabase`.

### B6 - Withdrawal API
- **Todo:** `POST /accounts/{accountNumber}/withdrawals`, with the same pattern, a `WDR` reference and a funds check.
- **Acceptance criteria:**
  - [x] A valid withdrawal decreases the balance and records one `WITHDRAWAL` transaction.
  - [x] An amount greater than the balance returns 409, and the balance and history are unchanged.
  - [x] Withdrawing exactly the balance succeeds and leaves 0.00.
  - [x] A non-ACTIVE account returns 409. An invalid amount returns 400. An unknown account returns 404.
- **Relevant context:** [`WithdrawalFrame.java`](src/main/java/com/legacybank/WithdrawalFrame.java).
- **Status:** [x] done (issue #14). Reuses B5 building blocks; added `AccountRules.requireSufficientFunds` for B7.

### B7 - Transfer API
- **Todo:** `POST /transfers` with `fromAccount`, `toAccount`, `amount` and an optional `description`. It runs one DB transaction with a `TRF` reference shared by the `TRANSFER_DEBIT` and `TRANSFER_CREDIT` rows.
- **Acceptance criteria:**
  - [x] A valid transfer moves funds, writes 2 transaction rows with the same reference, and the total across both accounts is unchanged.
  - [x] The same source and destination returns 400.
  - [x] A non-ACTIVE source or destination returns 409. Insufficient funds returns 409. An unknown account returns 404.
  - [x] A failure on the credit leg rolls back the debit and both transaction rows (the legacy `transferRollsBackEverythingOnFailure` scenario).
- **Relevant context:** [`TransferFrame.java`](src/main/java/com/legacybank/TransferFrame.java).
- **Status:** [x] done (issue #15). Endpoint: `POST /api/v1/transfers`.

### B8 - Transaction history API
- **Todo:** `GET /accounts/{accountNumber}/transactions?type=` returns rows newest first, with an optional type filter.
- **Acceptance criteria:**
  - [x] Results are ordered newest first (tested with seed data).
  - [x] `type` filters to one of the 4 valid types. An invalid type returns 400.
  - [x] An unknown account returns 404. An account with no history returns an empty list.
  - [x] Each row has type, amount, description, date and reference number.
- **Relevant context:** [`TransactionHistoryFrame.java`](src/main/java/com/legacybank/TransactionHistoryFrame.java).
- **Status:** [x] done (issue #16). Endpoint: `GET /api/v1/accounts/{accountNumber}/transactions?type=`.

### B9 - Dashboard summary API
- **Intent:** Feed the new dashboard (a new capability, not in legacy).
- **Todo:** `GET /dashboard/summary` returns customer count, account count, total balance of ACTIVE accounts, and the 5-10 most recent transactions across all accounts.
- **Acceptance criteria:**
  - [x] With seed data, the counts are 10 customers and 15 accounts, and the balance total matches a direct SQL sum of ACTIVE accounts.
  - [x] Recent transactions are newest first and limited in number.
  - [x] The endpoint requires authentication.
- **Status:** [x] done (issue #17). Endpoint: `GET /api/v1/dashboard/summary` (recent transactions limited to 10, each with account number and customer name).

---

## Epic: Frontend
Goal: a clean Carbon-based React + TypeScript UI for all legacy workflows, with a presentable dashboard.

### F1 - Scaffold React + TypeScript app with Carbon
- **Todo:**
  1. Create `modern/frontend` (Vite, React, TypeScript, `@carbon/react`, `sass`, React Router, Vitest + Testing Library).
  2. Add a typed API client with the base URL from an env variable, a Dev proxy or CORS setup, an auth context that stores the token, and a protected-route wrapper.
- **Acceptance criteria:**
  - [x] `npm run dev`, `npm run build` and `npm test` succeed.
  - [x] Visiting a protected route without a token redirects to `/login`.
  - [x] A 401 API response clears the token and redirects to `/login`.
- **Status:** [x] done (issue #18). Vite proxies `/api` to the backend in dev (`VITE_PROXY_TARGET`, default `http://localhost:8080`), so no CORS is needed. `VITE_API_BASE_URL` overrides the API base URL.

### F2 - Login page
- **Acceptance criteria:**
  - [x] The form shows labeled username and password fields (Carbon `TextInput`/`PasswordInput`) and a Sign in button.
  - [x] A valid login goes to the dashboard. A failed login shows an inline error.
  - [x] The button is disabled while submitting, and empty fields are not submitted.
  - [x] Component tests cover success and failure with a mocked API.
- **Relevant context:** [`LoginFrame.java`](src/main/java/com/legacybank/LoginFrame.java).
- **Status:** [x] done (issue #19). Route `/login`; a signed-in user is redirected away from it; after login it returns to the originally requested page.

### F3 - App shell and modern dashboard (UI enhancement)
- **Intent:** The demo-facing screen.
- **Todo:** Carbon `Header` with the user name and a Logout action, a `SideNav` for the main screens, KPI `Tile`s (customers, accounts, total balance), a Quick Actions panel, and a Recent Transactions `DataTable` with colored `Tag`s per type. Loading and error states use Carbon skeleton and notification components. Money is formatted as currency.
- **Acceptance criteria:**
  - [x] The dashboard shows data from `/dashboard/summary`. Skeletons show while loading and an inline error shows on failure.
  - [x] Quick actions and nav items navigate to the Deposit, Withdraw, Transfer, Search and History screens.
  - [x] Logout clears the token and returns to login.
  - [x] The layout is usable at desktop and tablet widths, and interactive elements are keyboard-reachable.
  - [x] A component test covers rendering with mocked summary data.
- **Relevant context:** [`MainFrame.java`](src/main/java/com/legacybank/MainFrame.java).
- **Status:** [x] done (issue #20). Layout reviewed on desktop and tablet screenshots. Routes `/search`, `/deposit`, `/withdraw`, `/transfer`, `/history` are placeholders until F4-F7.

### F4 - Customer search and account details screens
- **Acceptance criteria:**
  - [x] The user can search by customer ID or account number, and results show customer info and an accounts table.
  - [x] An account detail view shows type, status Tag, balance and owner, with buttons to Deposit, Withdraw and History for that account.
  - [x] Not-found and invalid-input errors show friendly messages.
- **Relevant context:** [`CustomerSearchFrame.java`](src/main/java/com/legacybank/CustomerSearchFrame.java), [`AccountDetailsFrame.java`](src/main/java/com/legacybank/AccountDetailsFrame.java).
- **Status:** [x] done (issue #21). Routes `/search` and `/accounts/:accountNumber`. Account action buttons open `/deposit`, `/withdraw` and `/history` with `?account=<number>` (F5-F7 read it to prefill).

### F5 - Deposit and withdrawal screens
- **Acceptance criteria:**
  - [x] Each form takes an account number, amount and optional description, with client-side checks for amount > 0, at most 2 decimals and at most 10,000,000.
  - [x] A success message shows the reference number and the new balance.
  - [x] Server errors (inactive account, insufficient funds) show clearly and the form stays editable.
  - [x] Component tests cover validation, success and server error.
- **Relevant context:** [`DepositFrame.java`](src/main/java/com/legacybank/DepositFrame.java), [`WithdrawalFrame.java`](src/main/java/com/legacybank/WithdrawalFrame.java).
- **Status:** [x] done (issue #22). Routes `/deposit` and `/withdraw` share `AmountForm`; both prefill the account from `?account=`. Client checks live in `validation.ts` and mirror the backend `BankValidator`.

### F6 - Transfer screen
- **Acceptance criteria:**
  - [ ] The form takes source account, destination account, amount and optional description. It blocks identical accounts client-side.
  - [ ] A success message shows the shared reference number.
  - [ ] Server errors are displayed, and double submission is prevented.
  - [ ] Component tests cover validation, success and server error.
- **Relevant context:** [`TransferFrame.java`](src/main/java/com/legacybank/TransferFrame.java).
- **Status:** [ ] pending

### F7 - Transaction history screen
- **Acceptance criteria:**
  - [ ] A table lists transactions newest first, with a type filter (All, DEPOSIT, WITHDRAWAL, TRANSFER_DEBIT, TRANSFER_CREDIT).
  - [ ] The empty state and the not-found account error are handled.
  - [ ] Amounts are formatted as currency and types are shown as Tags.
  - [ ] A component test covers filtering with mocked data.
- **Relevant context:** [`TransactionHistoryFrame.java`](src/main/java/com/legacybank/TransactionHistoryFrame.java).
- **Status:** [ ] pending

---

## Epic: DevOps
Goal: run and verify the whole app through Docker Compose, with documented local instructions.

### O1 - Backend Dockerfile
- **Acceptance criteria:**
  - [ ] A multi-stage build produces a Java 21 runtime image, and the container starts.
  - [ ] The DB path is configurable through an env variable and mapped to a mounted volume.
  - [ ] The image contains no `*.db` file and no secrets.
- **Status:** [ ] pending

### O2 - Frontend Dockerfile
- **Acceptance criteria:**
  - [ ] A multi-stage build serves the production build through nginx.
  - [ ] `/api` is proxied to the backend service, or the API base URL is configurable.
  - [ ] The container serves the app on a documented port.
- **Status:** [ ] pending

### O3 - Docker Compose setup
- **Acceptance criteria:**
  - [ ] `docker compose up --build` starts the frontend and backend.
  - [ ] The SQLite DB lives on a named volume and is created from `schema.sql` and `seed.sql` when missing (via the D2 script or an init step).
  - [ ] Login with the demo credentials works through the frontend on the Compose stack.
  - [ ] Data survives `docker compose restart`. The Compose file never mounts the legacy `legacybank.db`.
- **Status:** [ ] pending

### O4 - README and repository hygiene
- **Acceptance criteria:**
  - [ ] `modern/README.md` documents prerequisites, local run (backend, frontend), Docker run, tests and the demo credentials.
  - [ ] `.gitignore` excludes `target/`, `node_modules/`, `*.db`, `dist/` and `.env`.
  - [ ] `git status` shows no build output or database files staged.
- **Status:** [ ] pending

---

## Final Validation (Stage 3, after all Stories)
- Run `mvn test` in the backend and `npm test` in the frontend, then run the D3 check.
- Walk through the demo checklist: dashboard, login, customer lookup, deposit, withdrawal, transfer, history.
- Run `docker compose up --build` and repeat a smoke test.
- Open a PR from `feature/modern-banking-app` to `main` in `origin`. Do not merge without asking.

## Implementation guidance (Agent mode)
- Use `start_subtask` once per Story and tell it to read this plan file first.
- After each Story, tick the acceptance criteria and update the Status in this file, comment on and close the GitHub Issue, commit, and push to `origin`.
- Wait for the user's OK before starting the next Story.
