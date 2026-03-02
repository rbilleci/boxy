# Docker Setup for Boxy Evaluation

This guide explains how to quickly evaluate Boxy using Docker and Docker Compose.

## Quick Start (Recommended)

The fastest way to get Boxy running locally is with Docker Compose:

```bash
# From the boxy root directory
cd docker
docker-compose up -d
```

This starts:
- MySQL 8.0 with the Boxy schema pre-initialized
- phpMyAdmin on http://localhost:8080 (optional, for visual inspection)

### Check Status

```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs -f mysql

# Connect to MySQL
mysql -h 127.0.0.1 -u boxy -pboxy -D boxy
```

### Stop

```bash
docker-compose down
```

To also remove persistent data:

```bash
docker-compose down -v
```

## Docker Build

To build the Dockerfile directly (without Docker Compose):

```bash
docker build -t boxy-mysql:latest .
```

Then run it:

```bash
docker run \
  --name boxy-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=boxy \
  -e MYSQL_USER=boxy \
  -e MYSQL_PASSWORD=boxy \
  -p 3306:3306 \
  boxy-mysql:latest
```

## Evaluate Boxy in Docker

Once MySQL is running, you can evaluate Boxy by:

### 1. Connect to MySQL

```bash
mysql -h 127.0.0.1 -u boxy -pboxy -D boxy
```

### 2. Create a Namespace and Topic

```sql
-- Create namespace
CALL sp_namespaces__register('/', 'prod');

-- Create topic
CALL sp_topics__register('/', 'events', 4);

-- Verify
SELECT * FROM topics;
```

### 3. Create a Consumer Group and Subscribe

```sql
-- Register consumer
CALL sp_consumers__register('my-consumer-group');

-- Subscribe to topic
CALL sp_subscriptions__subscribe('my-consumer-group', '/', 'events');

-- List subscriptions
SELECT * FROM subscriptions;
```

### 4. Publish Events

```sql
-- Publish a single event
CALL sp_events__publish('/', 'events', 'key1', 'Hello from Boxy!');

-- Publish multiple events
CALL sp_events__publish_multi(
  '/', 'events',
  'key2,key3,key4',
  'Event 2,Event 3,Event 4'
);

-- View events
SELECT id, topic_id, partition_id, sequence, published_at, payload 
FROM events LIMIT 10;
```

### 5. Poll Events (as Consumer)

```sql
-- Poll events for the consumer group
CALL sp_events__poll(
  'my-consumer-group',        -- subscription name
  '/',                         -- path
  'events',                    -- topic name
  2,                           -- max_count
  '2025-03-02 00:00:00'       -- timeout (ISO 8601 or date string)
);

-- You should see events published above
```

### 6. Commit Cursors

```sql
-- After processing events, commit the cursor
-- (updates the position tracked for this consumer group)
CALL sp_cursors__commit(
  'my-consumer-group',
  '/',
  'events',
  0,                           -- partition_id
  5                            -- position (sequence of last processed event)
);
```

## Environment Variables

Docker Compose respects these environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_ROOT_PASSWORD` | `root` | MySQL root password |
| `MYSQL_DATABASE` | `boxy` | Default database name |
| `MYSQL_USER` | `boxy` | Non-root user for application |
| `MYSQL_PASSWORD` | `boxy` | Non-root user password |

To override, set them before running:

```bash
export MYSQL_ROOT_PASSWORD=supersecret
export MYSQL_USER=myapp
export MYSQL_PASSWORD=mypassword
docker-compose up -d
```

## Performance Tuning

For evaluation purposes, MySQL in Docker is configured with relaxed durability:

```yaml
environment:
  MYSQL_INITDB_SKIP_TZINFO: "yes"
```

For production-like benchmarking, modify `docker-compose.yml` to add:

```yaml
command: >
  --max_connections=1000
  --innodb_buffer_pool_size=2G
  --innodb_log_file_size=512M
  --innodb_flush_log_at_trx_commit=1
  --sync_binlog=1
```

## Cleanup

```bash
# Remove containers
docker-compose down

# Remove images
docker rmi boxy-mysql:latest

# Remove all Boxy-related volumes
docker volume prune
```

## Troubleshooting

### MySQL container won't start

```bash
# Check logs
docker-compose logs mysql

# Ensure port 3306 is not already in use
lsof -i :3306

# Try with a different port in docker-compose.yml
ports:
  - "3307:3306"  # Changed from 3306:3306
```

### Schema initialization fails

```bash
# Verify the migrations directory exists
ls boxy-db/src/main/resources/db/changelog/mysql/

# Rebuild without cache
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

### Connection refused

Wait 10-15 seconds for MySQL to fully initialize:

```bash
# Watch logs until "ready for connections" appears
docker-compose logs -f mysql
```

## Next Steps

- **Run integration tests**: `mvn clean verify` (tests auto-start Docker MySQL)
- **Build the CLI**: `mvn clean package` in `boxy-cli/`
- **Benchmark**: `mvn clean verify -Pbench-local`
- **Read docs**: See `docs/` for more on architecture, SLAs, and performance tuning
