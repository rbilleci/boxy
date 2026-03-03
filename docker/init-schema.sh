#!/bin/bash
# Initialize Boxy stored procedures, functions, and events.
# This runs after mysql.schema.sql (01-schema.sql) has created the tables.
#
# Note: For full Liquibase-managed initialization, use docker-compose.yml
# which runs the official Liquibase image. This script is a fallback for
# the standalone Dockerfile that loads SPs directly via mysql client.

set -e

MYSQL_CMD="mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE}"

echo "[boxy] Loading stored procedures and functions..."

# Load functions first (SPs may depend on them)
for f in /docker-entrypoint-initdb.d/mysql/fn/*.sql; do
    if [ -f "$f" ]; then
        echo "[boxy]   Loading function: $(basename "$f")"
        $MYSQL_CMD < "$f"
    fi
done

# Load stored procedures
for f in /docker-entrypoint-initdb.d/mysql/sp/*.sql; do
    if [ -f "$f" ]; then
        echo "[boxy]   Loading procedure: $(basename "$f")"
        # SPs use DELIMITER // convention; strip it for mysql client
        sed 's|^DELIMITER //$||; s|^//$||; s|^DELIMITER ;$||' "$f" | $MYSQL_CMD
    fi
done

# Load scheduled events
for f in /docker-entrypoint-initdb.d/mysql/events/*.sql; do
    if [ -f "$f" ]; then
        echo "[boxy]   Loading event: $(basename "$f")"
        sed 's|^DELIMITER //$||; s|^//$||; s|^DELIMITER ;$||' "$f" | $MYSQL_CMD
    fi
done

echo "[boxy] Schema initialization complete."
