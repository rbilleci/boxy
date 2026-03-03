# Dockerfile for Boxy standalone evaluation
#
# Builds a MySQL 8.0 image with the Boxy schema pre-loaded.
# Requires building from the repository root:
#   docker build -f docker/Dockerfile -t boxy-eval:latest .
#
# Run:
#   docker run -p 3306:3306 boxy-eval:latest
#   mysql -h 127.0.0.1 -u boxy -pboxy -D boxy

FROM mysql:8.0.43

ENV MYSQL_ROOT_PASSWORD=root \
    MYSQL_DATABASE=boxy \
    MYSQL_USER=boxy \
    MYSQL_PASSWORD=boxy

# Copy the schema SQL file into the init directory.
# MySQL auto-executes *.sql files in this directory on first start.
COPY boxy-db/src/main/resources/db/changelog/mysql/mysql.schema.sql \
     /docker-entrypoint-initdb.d/01-schema.sql

# Copy stored procedures, functions, and events as init scripts.
# These must run after the schema, so we prefix with higher numbers.
COPY docker/init-schema.sh /docker-entrypoint-initdb.d/02-init-schema.sh
RUN chmod +x /docker-entrypoint-initdb.d/02-init-schema.sh

# MySQL performance tuning for evaluation
CMD ["mysqld", \
     "--log-bin-trust-function-creators=1", \
     "--event_scheduler=ON", \
     "--innodb_flush_log_at_trx_commit=0", \
     "--sync_binlog=0", \
     "--innodb_autoinc_lock_mode=2"]

EXPOSE 3306

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} || exit 1
