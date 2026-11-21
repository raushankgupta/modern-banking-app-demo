#!/usr/bin/env bash
# Evidence script for the modernization: proves (1) the legacy assets are unchanged and
# (2) the modern database enforces its integrity rules. Read-only: it never writes to the
# legacy tree or to legacybank.db, and builds a throwaway database in a temp directory.
#
# Usage:  modern/database/verify.sh
#
# Environment (all optional):
#   LEGACY_DB_BASELINE  file in `shasum -a 256` format holding the legacy DB hash, recorded
#                       BEFORE any work and kept outside the repo, e.g.:
#                         (cd <repo> && shasum -a 256 legacybank.db > /safe/place/legacy-db.sha256)
#                       When unset the hash check is reported as SKIP; when set it must match.
#   LEGACY_BASE_REF     git ref the legacy files are compared against (default: origin/main,
#                       then main). The comparison point is the merge-base with HEAD.
#   LEGACY_REPO         git work tree to check (default: this repository). Lets the check be
#                       exercised against a scratch clone without touching the real legacy tree.
#
# Exit code: 0 when nothing failed, 1 otherwise.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
ROOT="${LEGACY_REPO:-$DEFAULT_ROOT}"
# 'run.*' is a git pathspec glob, so a new run.sh would be caught too.
LEGACY_PATHS=(src database pom.xml 'run.*')

PASSED=0
FAILED=0
SKIPPED=0
pass() { PASSED=$((PASSED + 1)); printf 'PASS  %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf 'FAIL  %s\n' "$1"; }
skip() { SKIPPED=$((SKIPPED + 1)); printf 'SKIP  %s\n' "$1"; }

sha256_check() { # file in `shasum -a 256` format, run from $ROOT
  if command -v shasum >/dev/null; then shasum -a 256 -c "$1"; else sha256sum -c "$1"; fi
}

# ---------------------------------------------------------------------------------------
echo "== Legacy immutability (repo: $ROOT) =="

BASE=""
for ref in "${LEGACY_BASE_REF:-origin/main}" main; do
  if BASE="$(git -C "$ROOT" merge-base HEAD "$ref" 2>/dev/null)" && [[ -n "$BASE" ]]; then
    BASE_REF="$ref"
    break
  fi
  BASE=""
done

if [[ -z "$BASE" ]]; then
  fail "cannot find a base commit to compare against (tried ${LEGACY_BASE_REF:-origin/main} and main)"
else
  # Compares the work tree (committed, staged and unstaged changes) with the branch point.
  if git -C "$ROOT" diff --quiet "$BASE" -- "${LEGACY_PATHS[@]}"; then
    pass "legacy paths identical to $BASE_REF at ${BASE:0:7}: ${LEGACY_PATHS[*]}"
  else
    fail "legacy paths differ from $BASE_REF at ${BASE:0:7}:"
    git -C "$ROOT" diff --stat "$BASE" -- "${LEGACY_PATHS[@]}" | sed 's/^/        /'
  fi
fi

STRAY="$(git -C "$ROOT" status --porcelain --untracked-files=all -- "${LEGACY_PATHS[@]}")"
if [[ -z "$STRAY" ]]; then
  pass "no modified, staged or untracked files under the legacy paths"
else
  fail "git status reports changes under the legacy paths:"
  printf '%s\n' "$STRAY" | sed 's/^/        /'
fi

if [[ -z "${LEGACY_DB_BASELINE:-}" ]]; then
  skip "legacybank.db hash not checked (set LEGACY_DB_BASELINE to a baseline recorded before work)"
elif [[ ! -f "$LEGACY_DB_BASELINE" ]]; then
  fail "baseline file not found: $LEGACY_DB_BASELINE"
elif OUT="$(cd "$ROOT" && sha256_check "$LEGACY_DB_BASELINE" 2>&1)"; then
  pass "legacybank.db hash matches the baseline"
else
  fail "legacybank.db does not match the baseline: $OUT"
fi

# ---------------------------------------------------------------------------------------
echo
echo "== Modern database integrity =="

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is required" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
DB="$TMP/integrity.db"

if ! "$SCRIPT_DIR/init.sh" "$DB" >"$TMP/init.log" 2>&1; then
  fail "init.sh could not build a fresh modern database"
  sed 's/^/        /' "$TMP/init.log"
  echo
  echo "== Summary: $PASSED passed, $((FAILED)) failed, $SKIPPED skipped =="
  exit 1
fi

CUSTOMERS="$(sqlite3 "$DB" 'SELECT COUNT(*) FROM CUSTOMERS')"
ACCOUNTS="$(sqlite3 "$DB" 'SELECT COUNT(*) FROM ACCOUNTS')"
pass "fresh database built from schema.sql + seed.sql ($CUSTOMERS customers, $ACCOUNTS accounts)"

SQL_PREFIX='PRAGMA foreign_keys=ON;'
ANY_CUSTOMER='(SELECT MIN(CUSTOMER_ID) FROM CUSTOMERS)'
ANY_ACCOUNT='(SELECT MIN(ACCOUNT_NUMBER) FROM ACCOUNTS)'

# expect_rejected <description> <expected error text> <sql>
expect_rejected() {
  local out
  if out="$(sqlite3 "$DB" "$SQL_PREFIX $3" 2>&1)"; then
    fail "$1: statement was accepted"
  elif grep -qi "$2" <<<"$out"; then
    pass "$1 -> rejected ($2)"
  else
    fail "$1: rejected for the wrong reason: $out"
  fi
}

# Positive control (rolled back): proves the rejections below come from the constraints,
# not from a typo in the test statements.
if out="$(sqlite3 "$DB" "$SQL_PREFIX BEGIN;
  INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
    VALUES ('VERIFY0001', $ANY_CUSTOMER, 'SAVINGS', 10.00, 'ACTIVE');
  INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, DESCRIPTION, REFERENCE_NUMBER)
    VALUES ('VERIFY0001', 'DEPOSIT', 10.00, 'control', 'DEP9999999999');
  ROLLBACK;" 2>&1)"; then
  pass "a valid account and transaction are accepted (control)"
else
  fail "a valid account and transaction were rejected: $out"
fi

expect_rejected "negative balance on insert" "CHECK constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   VALUES ('VERIFY0002', $ANY_CUSTOMER, 'SAVINGS', -0.01, 'ACTIVE');"

expect_rejected "overdraft (balance updated below zero)" "CHECK constraint failed" \
  "UPDATE ACCOUNTS SET BALANCE = -1 WHERE ACCOUNT_NUMBER = $ANY_ACCOUNT;"

expect_rejected "invalid account status" "CHECK constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   VALUES ('VERIFY0003', $ANY_CUSTOMER, 'SAVINGS', 1, 'SUSPENDED');"

expect_rejected "invalid account type" "CHECK constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   VALUES ('VERIFY0004', $ANY_CUSTOMER, 'CRYPTO', 1, 'ACTIVE');"

expect_rejected "invalid transaction type" "CHECK constraint failed" \
  "INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, REFERENCE_NUMBER)
   VALUES ($ANY_ACCOUNT, 'REFUND', 5, 'REF1');"

expect_rejected "zero transaction amount" "CHECK constraint failed" \
  "INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, REFERENCE_NUMBER)
   VALUES ($ANY_ACCOUNT, 'DEPOSIT', 0, 'REF2');"

expect_rejected "negative transaction amount" "CHECK constraint failed" \
  "INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, REFERENCE_NUMBER)
   VALUES ($ANY_ACCOUNT, 'DEPOSIT', -5, 'REF3');"

expect_rejected "duplicate account number" "UNIQUE constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   SELECT ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, 1, 'ACTIVE' FROM ACCOUNTS LIMIT 1;"

expect_rejected "duplicate employee username" "UNIQUE constraint failed" \
  "INSERT INTO EMPLOYEES (USERNAME, PASSWORD, EMPLOYEE_NAME)
   SELECT USERNAME, 'x', 'Duplicate' FROM EMPLOYEES LIMIT 1;"

expect_rejected "missing account number" "NOT NULL constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   VALUES (NULL, $ANY_CUSTOMER, 'SAVINGS', 1, 'ACTIVE');"

expect_rejected "orphan transaction (account does not exist)" "FOREIGN KEY constraint failed" \
  "INSERT INTO TRANSACTIONS (ACCOUNT_NUMBER, TRANSACTION_TYPE, AMOUNT, REFERENCE_NUMBER)
   VALUES ('NOSUCHACCT', 'DEPOSIT', 5, 'REF4');"

expect_rejected "account for a customer that does not exist" "FOREIGN KEY constraint failed" \
  "INSERT INTO ACCOUNTS (ACCOUNT_NUMBER, CUSTOMER_ID, ACCOUNT_TYPE, BALANCE, STATUS)
   VALUES ('VERIFY0005', -1, 'SAVINGS', 1, 'ACTIVE');"

# The rejected statements must have left the database exactly as seeded.
if [[ "$(sqlite3 "$DB" 'SELECT COUNT(*) FROM ACCOUNTS')" == "$ACCOUNTS" \
   && "$(sqlite3 "$DB" "SELECT COUNT(*) FROM ACCOUNTS WHERE ACCOUNT_NUMBER LIKE 'VERIFY%'")" == "0" \
   && "$(sqlite3 "$DB" 'SELECT COUNT(*) FROM ACCOUNTS WHERE BALANCE < 0')" == "0" ]]; then
  pass "database unchanged after all rejected statements"
else
  fail "database content changed after the rejected statements"
fi

# ---------------------------------------------------------------------------------------
echo
echo "== Summary: $PASSED passed, $FAILED failed, $SKIPPED skipped =="
[[ "$FAILED" -eq 0 ]]
