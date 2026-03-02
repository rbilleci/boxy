# Dockerfile for Boxy evaluation environment
# MySQL 8.0 with Boxy schema pre-initialized
# Usage: docker build -t boxy-mysql:latest .
#        docker run -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root boxy-mysql:latest

FROM mysql:8.0.36-debian

# Set MySQL environment
ENV MYSQL_ROOT_PASSWORD=root \
    MYSQL_DATABASE=boxy \
    MYSQL_USER=boxy \
    MYSQL_PASSWORD=boxy

# Copy Liquibase migrations into Docker
# These will be run during container startup (Docker uses /docker-entrypoint-initdb.d/)
COPY ./boxy-db/src/main/resources/db/changelog/ /docker-entrypoint-initdb.d/

# Copy a script to initialize the schema
COPY ./docker/init-schema.sh /docker-entrypoint-initdb.d/01-init-schema.sh

# Make init script executable
RUN chmod +x /docker-entrypoint-initdb.d/01-init-schema.sh

# Expose MySQL port
EXPOSE 3306

# Healthcheck: verify MySQL is responsive
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} || exit 1
