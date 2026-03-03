# Docker Setup for Boxy Evaluation

This guide explains how to quickly evaluate Boxy using Docker and Docker Compose.

## Quick Start (Recommended)

The fastest way to get Boxy running locally is with Docker Compose. This uses the
official Liquibase Docker image to apply all schema migrations, stored procedures,
and functions automatically.

```bash
# From the repository root
cd docker
docker compose up -d
```

This starts MySQL 8.0 and runs Liquibase migrations to initialize the full Boxy schema
(tables, stored procedures, functions, and scheduled events).

Wait for the `liquibase-mysql` container to exit successfully:

```bash
docker compose logs liquibase-mysql
# Look for: "Liquibase command 'update' was executed successfully."
```

### Connect to MySQL

```bash
mysql -h 127.0.0.1 -u boxy -pboxy -D boxy
```

### PostgreSQL (Optional)

To also start a PostgreSQL instance with the Boxy schema:

```bash
docker compose --profile postgres up -d
```

Connect:

```bash
psql -h 127.0.0.1 -U boxy -d boxy
# Password: boxy
```

### phpMyAdmin (Optional)

For visual inspection of the MySQL database:

```bash
docker compose --profile admin up -d
```

Then open http://localhost:8080 in your browser.

### Stop

```bash
docker compose down        # Stop containers
docker compose down -v     # Stop and remove persistent data
```

## Evaluate Boxy

Once the database is running and migrations are applied:

### 1. Create a Namespace and Topic

```sql
CALL sp_namespaces__create('/', 'prod');
CALL sp_topics__create('/prod', 'events', 4);
SELECT * FROM topics;
```

### 2. Create a Subscription

```sql
CALL sp_subscriptions__create('my-group');
CALL sp_subscriptions__subscribe('my-group', '/prod/events');
```

### 3. Publish Events

```sql
CALL sp_events__publish('/prod', 'events', 'key1', 'Hello from Boxy!');
```

### 4. Register a Consumer and Poll

```sql
CALL sp_consumers__register('11111111-1111-1111-1111-111111111111', 'my-group', '["\/prod\/events"]');
CALL sp_events__poll('11111111-1111-1111-1111-111111111111', 100);
```

## Standalone Dockerfile

To build without Docker Compose (loads schema and SPs via shell script):

```bash
# Build from repository root
docker build -f docker/Dockerfile -t boxy-eval:latest .

# Run
docker run -p 3306:3306 boxy-eval:latest
```

Note: The standalone Dockerfile approach uses direct SQL loading rather than
Liquibase, so it may not include the latest changeset additions. The Docker Compose
approach is recommended.

## Troubleshooting

**MySQL container won't start:** Check `docker compose logs mysql` and ensure
port 3306 is free (`lsof -i :3306`).

**Liquibase migration fails:** Check `docker compose logs liquibase-mysql`. Common
issues include the MySQL container not being fully ready (the healthcheck should
handle this) or SQL syntax errors in stored procedures.

**Connection refused:** Wait 10-15 seconds for MySQL to fully initialize, then retry.
