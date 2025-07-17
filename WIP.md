
# Rethinking the Architecture

## Event Consumption / Workers
#### Functions
We need a "worker" API for each major programming language (Java, Python, JS, Rust, ...):
- For reference, examine the worker API from temporal.io. This is an indicator about the languages to support.
- They need `leases`, `event` reading/polling, and `offset` management. 
- These workers may also need to "submit" events. It may be they want to signal something else happened.
#### Performance
This is the critical part of the system, where we need:
- high throughput
- low latency
- resilience

#### Options
1. EMBEDDED/NATIVE: compile to native, make available to use in other languages. 
Perhaps provide simplified wrapper. We would need about `5` builds for the native image, 
then for every programming language we need to test `5` times. 
So if we support 10 programming languages we have about `50` builds?
2. EMBEDDED/LANGUAGE-SPECIFIC: We write a complete implementation of the workers per language.
This may mean we have more code to manage. For this scenario, we would want to use Smithy.
3. SIDECAR: we rely on a locally running broker.
We communicate with that broker over `Unix Domain Sockets (UDS)`,  but that is not as fast as shared memory.
Ideally we use shared memory libraries, or solutions like Chronicle Queue.

#### Comparisons
For the EMBEDDED/NATIVE case, we can leverage Chronicle Queue to pull down events locally, ahead of
the processors. BUT, is this extra speed needed at all?

## Event Producers
#### Functions
For submitting to the transactional outbox, we need to ask users to simply call a stored procedure. 
We can progressively support the following:
- Provide framework specific libs in the major ORM systems. Support JOOQ, JPA, etc...
- Provide support for things like Spring/Quarkus... making it easy to use.
#### Performance
Event producing must be blazing fast, happening within the transaction. There should by little to know overhead.
We'll write specialized libraries. Event submission is so simple, we don't need any solutions like Smithy.

### Topic, Consumer Group, and Subscription Management
For managing the solution we need to support: `topic` management, `subscription` management, 
`consumer group` management, etc...

--- 
# Priorities
# P1
- hikari cp
- jdbc setup
- starting value for offsets

# P2 
- get up and running
- benchmark workers

# P3
- Batching submissions
- maximum lease duration... (allow for equal distribution)
- agnostic + support for postgres
- heartbearts on leases, resilience
- stasher / reaper
- smithy
- server timestamp?
- paging objects
- table partitioning
- event cleanup
- blob archival
- support go, python, and rust
- cleanup expired leases
- reconsider "keys"
- AWS JDBC driver

# Smithy 
1. All of the client implementation interfaces over the broker/sidecar
2. server stubs to interact with the db
3. lightweight, low-memory overhead
4. broker running as LAMBDA
5. broker as sidecar
6. broker as embedded
7. handle AuthN/AuthZ


# Reference Systems on github
- goharvest
- goneli