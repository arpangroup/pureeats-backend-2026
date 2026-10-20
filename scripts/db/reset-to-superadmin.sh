#!/usr/bin/env bash
# =====================================================================================================================
# PureEats: back up the database, then delete ALL data except the super admin account (see reset-to-superadmin.sql).
#
# !!! IRREVERSIBLE apart from the backup this takes first. Stop the backend before running it. !!!
#
#   CONFIRM=DELETE_ALL_DATA ./scripts/db/reset-to-superadmin.sh
#
# Connection: DATABASE_URL (postgres://user:pass@host:5432/db) or the usual PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE,
# or DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD (the same variables the backend reads).
# Options:
#   SUPERADMIN_EMAIL  account to keep (default superadmin@pureeats.in)
#   KEEP_CONFIG=true  also keep app configuration (settings, payment/SMS gateways, modules, translations, pages)
#   BACKUP_DIR        where the pg_dump goes (default ./backups)
# =====================================================================================================================
set -euo pipefail

if [[ "${CONFIRM:-}" != "DELETE_ALL_DATA" ]]; then
  echo "This deletes every row except the super admin. Re-run with CONFIRM=DELETE_ALL_DATA to proceed." >&2
  exit 1
fi

SUPERADMIN_EMAIL="${SUPERADMIN_EMAIL:-superadmin@pureeats.in}"
KEEP_CONFIG="${KEEP_CONFIG:-false}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Same variables the backend uses, mapped onto libpq's.
export PGHOST="${PGHOST:-${DB_HOST:-localhost}}"
export PGPORT="${PGPORT:-${DB_PORT:-5432}}"
export PGDATABASE="${PGDATABASE:-${DB_NAME:-pureeats}}"
export PGUSER="${PGUSER:-${DB_USERNAME:-postgres}}"
if [[ -n "${DB_PASSWORD:-}" && -z "${PGPASSWORD:-}" ]]; then export PGPASSWORD="$DB_PASSWORD"; fi
CONN=()
if [[ -n "${DATABASE_URL:-}" ]]; then CONN=("$DATABASE_URL"); fi

TARGET="${DATABASE_URL:-${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}}"
echo "Target database : ${TARGET%%\?*}"
echo "Keeping         : ${SUPERADMIN_EMAIL} (keep_config=${KEEP_CONFIG})"

mkdir -p "$BACKUP_DIR"
BACKUP="$BACKUP_DIR/pureeats-before-reset-$(date +%Y%m%d-%H%M%S).dump"
echo "Backing up to   : $BACKUP"
pg_dump "${CONN[@]}" --format=custom --blobs --file="$BACKUP"
echo "Backup done ($(du -h "$BACKUP" | cut -f1)). Restore with: pg_restore --clean --if-exists -d <db> \"$BACKUP\""

psql "${CONN[@]}" -v ON_ERROR_STOP=1 \
  -v confirm=DELETE_ALL_DATA \
  -v superadmin_email="$SUPERADMIN_EMAIL" \
  -v keep_config="$KEEP_CONFIG" \
  -f "$SCRIPT_DIR/reset-to-superadmin.sql"

echo "Reset complete. Start the backend (with pureeats.seed.demo-data.enabled=false so demo data isn't re-seeded)."
