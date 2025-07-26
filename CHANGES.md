# Decentralized Randomized Lease Distribution (Work-Stealing) Implementation

## Overview

This implementation adapts the lease system to use a decentralized, randomized work-stealing algorithm for consumer-group partitions. The key changes include:

1. Removing the `expires_at` and `updated_at` columns from the lease table
2. Adding a `status` column to track when a lease is in a RELEASING state
3. Creating a new `sp_workers_check_in` stored procedure for worker heartbeats and lease management
4. Updating existing stored procedures to work with the new schema

## Database Changes

### Lease Table Modifications

- Removed `expires_at` and `updated_at` columns
- Added `status` column (ENUM with values 'ACTIVE' and 'RELEASING')
- Added `release_started_at` column to track when a lease was marked as RELEASING
- Updated indexes to support efficient queries on the new columns

### New Stored Procedures

- `sp_workers_check_in`: Handles worker heartbeats, lease management, and fair share calculations

### Updated Stored Procedures

- `sp_leases_acquire`: Updated to use worker's last_heartbeat instead of lease's expires_at
- `sp_leases_release`: Updated to mark leases as RELEASING instead of deleting them
- `sp_leases_renew`: Updated to work without expires_at and to update worker's heartbeat

### Updated Views

- `leases_available_view`: Updated to use the status column instead of expires_at

## Java Code Changes

### Model Classes

- `Lease`: Updated to remove `updatedAt` and `expiresAt` fields and add `status` and `releaseStartedAt` fields

### DAO Classes

- `LeaseDao`: Updated to work with the new Lease model
- `WorkerDao`: Added a method for worker check-in that calls the new stored procedure

## How the New System Works

### Heartbeat Protocol

Workers call the `checkIn` method periodically, which:
1. Updates the worker's heartbeat timestamp
2. Calculates the ideal share of leases for the worker based on its weight
3. Releases excess leases by marking them as RELEASING
4. Acquires new leases if the worker has fewer than its ideal share
5. Returns statistics to the worker, including the ideal share and current number of leases

### Lease Expiration

Lease expiration is now determined based on the worker's last_heartbeat timestamp rather than an explicit expires_at timestamp on the lease. This simplifies the system and ensures that all leases for a worker expire at the same time.

### Lease Release

When a lease is released, it is marked as RELEASING instead of being immediately deleted. The actual deletion happens during worker check-in when the lease has been in the RELEASING state for more than 10 seconds.

### Worker Expiration

When a worker's heartbeat expires (last_heartbeat is older than the lease expiry time), its leases are explicitly deleted before the worker is deleted. This ensures that leases are properly cleaned up when a worker goes offline.

## Configuration Parameters

- `heartbeatInterval`: The interval in seconds between worker check-ins
- `weight`: The worker's capacity weight, used to calculate its ideal share of leases
- `batchSize`: The maximum number of leases to acquire in a single check-in

## Advantages Over Rendezvous-Hashing

- Reduced polling: Only one worker needs to check each inactive partition per cycle
- Improved latency: By staggering pivots, we can achieve sub-second response times with fewer database queries
- Better load balancing: Workers adapt quickly to changes in the number of active workers and partitions