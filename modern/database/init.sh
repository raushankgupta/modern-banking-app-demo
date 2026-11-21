#!/usr/bin/env bash
# Creates (or resets) the modern SQLite database from schema.sql and seed.sql.
#
# Usage:  modern/database/init.sh [target-db-path]
#   target-db-path  defaults to $DB_PATH, then modern/database/data/modernbank.db
#   ADMIN_PASSWORD  demo password to hash for the seeded admin user (default: admin123)
#
# The BCrypt hash of the admin password is generated here at run time and
# substituted for the __ADMIN_PASSWORD_BCRYPT__ placeholder in seed.sql, so no
# hash is ever committed to git. Needs: sqlite3, and htpasswd or python3+bcrypt.
# The script refuses to touch the legacy legacybank.db.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-${DB_PATH:-$SCRIPT_DIR/data/modernbank.db}}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
PLACEHOLDER="__ADMIN_PASSWORD_BCRYPT__"

# Resolve the target to an absolute path without requiring it to exist yet.
mkdir -p "$(dirname "$TARGET")"
TARGET="$(cd "$(dirname "$TARGET")" && pwd -P)/$(basename "$TARGET")"

# Guard: never touch the legacy database (by name, or by location in the legacy repo root).
LEGACY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
if [[ "$(basename "$TARGET")" == "legacybank.db" || "$TARGET" == "$LEGACY_ROOT/legacybank.db" ]]; then
  echo "ERROR: refusing to initialize the legacy database: $TARGET" >&2
  exit 1
fi

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is required" >&2; exit 1; }

hash_password() {
  if command -v htpasswd >/dev/null; then
    htpasswd -bnBC 10 "" "$1" | tr -d ':\n'
  elif python3 -c 'import bcrypt' 2>/dev/null; then
    python3 -c 'import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(10)).decode(), end="")' "$1"
  else
    echo "ERROR: need htpasswd (apache2-utils) or python3 with the bcrypt module" >&2
    return 1
  fi
}

HASH="$(hash_password "$ADMIN_PASSWORD")"
[[ "${#HASH}" -eq 60 && "$HASH" == \$2* ]] || { echo "ERROR: unexpected BCrypt hash output" >&2; exit 1; }

# Reset: remove any previous DB (and SQLite side files) so the result is always the seed state.
rm -f "$TARGET" "$TARGET-journal" "$TARGET-wal" "$TARGET-shm"

SEED_TMP="$(mktemp)"
trap 'rm -f "$SEED_TMP"' EXIT
sed "s|$PLACEHOLDER|$HASH|" "$SCRIPT_DIR/seed.sql" > "$SEED_TMP"

sqlite3 "$TARGET" "PRAGMA foreign_keys=ON;" ".read $SCRIPT_DIR/schema.sql" ".read $SEED_TMP"

COUNTS="$(sqlite3 "$TARGET" "select (select count(*) from EMPLOYEES)||'/'||(select count(*) from CUSTOMERS)||'/'||(select count(*) from ACCOUNTS)||'/'||(select count(*) from TRANSACTIONS);")"
echo "Initialized $TARGET (employees/customers/accounts/transactions = $COUNTS)"
