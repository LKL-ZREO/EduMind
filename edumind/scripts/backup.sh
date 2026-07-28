#!/bin/sh
set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
DB_NAME="${PGDATABASE:-postgres}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.dump"
TEMP_FILE="${BACKUP_FILE}.tmp"

cleanup() {
    rm -f "${TEMP_FILE}"
}
trap cleanup EXIT INT TERM

mkdir -p "${BACKUP_DIR}"
echo "[$(date)] Starting PostgreSQL backup: ${DB_NAME} -> ${BACKUP_FILE}"

pg_dump \
    --host="${PGHOST:-postgres}" \
    --port="${PGPORT:-5432}" \
    --username="${PGUSER:-postgres}" \
    --dbname="${DB_NAME}" \
    --format=custom \
    --compress=6 \
    --file="${TEMP_FILE}"

if [ ! -s "${TEMP_FILE}" ]; then
    echo "[$(date)] Backup failed: generated file is empty" >&2
    exit 1
fi

pg_restore --list "${TEMP_FILE}" >/dev/null
chmod 0600 "${TEMP_FILE}"
mv "${TEMP_FILE}" "${BACKUP_FILE}"
trap - EXIT INT TERM

echo "[$(date)] Backup completed: $(du -h "${BACKUP_FILE}" | cut -f1)"

DELETED="$(find "${BACKUP_DIR}" -name "${DB_NAME}_*.dump" -type f -mtime "+${RETENTION_DAYS}" -delete -print | wc -l | tr -d ' ')"
if [ "${DELETED}" -gt 0 ]; then
    echo "[$(date)] Removed ${DELETED} backup(s) older than ${RETENTION_DAYS} days"
fi
