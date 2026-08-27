#!/bin/sh

set -eu

MIGRATIONS_DIR="${MIGRATIONS_DIR:-/migrations}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-cyberflow}"

# MYSQL_PWD avoids exposing the password in the process command line.
export MYSQL_PWD="$MYSQL_PASSWORD"

mysql_exec() {
    mysql \
        --default-character-set=utf8mb4 \
        --protocol=TCP \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MYSQL_USER" \
        "$@"
}

echo "Waiting for MySQL at ${MYSQL_HOST}:${MYSQL_PORT}..."
until mysql_exec --database="$MYSQL_DATABASE" --connect-timeout=5 -e "SELECT 1" >/dev/null 2>&1; do
    sleep 2
done

mysql_exec --database="$MYSQL_DATABASE" -e "
    CREATE TABLE IF NOT EXISTS schema_migrations (
        version    VARCHAR(255) NOT NULL PRIMARY KEY,
        applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"

for migration_path in "$MIGRATIONS_DIR"/*.sql; do
    [ -f "$migration_path" ] || continue
    migration_name=$(basename "$migration_path")
    applied=$(mysql_exec --database="$MYSQL_DATABASE" -Nse \
        "SELECT COUNT(*) FROM schema_migrations WHERE version = '${migration_name}'")

    if [ "$applied" = "1" ]; then
        echo "Skipping applied migration: $migration_name"
        continue
    fi

    echo "Applying migration: $migration_name"
    mysql_exec --database="$MYSQL_DATABASE" < "$migration_path"
    mysql_exec --database="$MYSQL_DATABASE" -e \
        "INSERT INTO schema_migrations (version) VALUES ('${migration_name}')"
done

echo "Database migrations completed."
