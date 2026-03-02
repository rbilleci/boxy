#!/bin/bash
# Initialize Boxy database schema using Liquibase
# This script is run during Docker container startup

set -e

echo "Waiting for MySQL to be ready..."
until mysqladmin ping -h localhost -u root -p"${MYSQL_ROOT_PASSWORD}" &>/dev/null; do
    echo "MySQL is unavailable - sleeping..."
    sleep 1
done
echo "MySQL is up and ready!"

# Note: Actual Liquibase migrations are applied via the SQL files
# in /docker-entrypoint-initdb.d/ (mounted from boxy-db/src/main/resources/db/changelog/)
# Docker MySQL automatically runs all *.sql files in that directory
# in lexicographical order.

echo "Schema initialization complete. Boxy is ready!"
