

# MySQL Coding Guidelines

These standards and conventions ensure that stored procedures and 
SQL code are **consistent**, **performant**, and **maintainable**.

---

## 1. General Principles

* **Readability**: Write clear, self‑documenting code. Use whitespace and indentation to structure logic.
* **Composability**: Break complex logic into focused procedures that can be called in orchestration routines.
* **Performance**: Leverage proper indexing, minimize locking, and avoid unnecessary object creation.
* **Reliability**: Guard data modifications with transactions and robust error handling to prevent partial updates.

---

## 2. Naming Conventions

| Element          | Prefix | Style        | Example                     |
| ---------------- | ------ | ------------ | --------------------------- |
| Input Parameter  | `p_`   | `snake_case` | `p_consumer_group_id`       |
| Output Parameter | `p_`   | `snake_case` | `p_worker_id`               |
| Local Variable   | `v_`   | `snake_case` | `v_current_count`           |
| Temporary Table  | `tmp_` | `snake_case` | `tmp_leases_to_release`     |
| Stored Procedure | —      | `snake_case` | `sp_workers_gc`             |
| View             | —      | `snake_case` | `subscription_offsets_view` |
| Table            | —      | `snake_case` | `consumer_group_stats`      |

> **Tip:** Avoid reusing parameter names for local variables to prevent confusion.

---

## 3. Error Handling & Transactions

1. **Transaction Scope**:

    * Wrap any multi‑step data changes in `START TRANSACTION; ... COMMIT;` blocks.
2. **Exit Handlers**:

    * Use `DECLARE EXIT HANDLER FOR SQLEXCEPTION` at the top of a procedure to `ROLLBACK` on errors.
    * Example:

   ```sql
   DECLARE EXIT HANDLER FOR SQLEXCEPTION
   BEGIN
     ROLLBACK;
   END;
   START TRANSACTION;
   -- DML here
   COMMIT;
   ```
3. **Resignal for Read‑Only**:

    * For read‑only procedures (e.g., stats readers), use `RESIGNAL` to propagate errors instead of swallowing them.

---

## 4. Naming of Objects & Index Strategies

* Ensure **indexes** exist on all columns used in `JOIN`, `WHERE`, and `ORDER BY` clauses.
* Prefer **covering indexes** for views when filtering large datasets (e.g., `subscription_id`, `consumer_group_id`).
* Use descriptive index names: `idx_table_column1_column2`.

---

## 5. Temporary Data Handling

* **Avoid DDL** on each invocation: prefer derived tables or session‑level artifacts instead of creating/dropping temp tables repeatedly.
* For complex diffs (e.g., leases added vs. removed), consider client‑side aggregation or JSON result sets instead of physical tables.

---

## 6. Upsert & Lookup Patterns

1. **Efficient Upsert**:

   ```sql
   INSERT INTO workers (node_id, zone_id, ..., heartbeat_detected_at)
   VALUES (...)
   ON DUPLICATE KEY UPDATE
     heartbeat_detected_at = UTC_TIMESTAMP(),
     id = LAST_INSERT_ID(id);
   SET p_worker_id = LAST_INSERT_ID();
   ```

    * Uses `LAST_INSERT_ID(id)` to avoid a second `SELECT`.
2. **Conditional Inserts**:

    * Use `NOT EXISTS` in the `SELECT` source for `INSERT` to skip existing rows and reduce deadlocks.

   ```sql
   INSERT INTO leases (...)
   SELECT ...
   FROM subscription_offsets_view so
   WHERE NOT EXISTS (
     SELECT 1 FROM leases l
      WHERE l.subscription_id = so.subscription_id
   )
   ORDER BY RAND()
   LIMIT v_to_acquire;
   ```

---

## 7. Performance & Readability Tips

* **Single‐Pass Aggregation**: Use `COUNT(*)` and `MAX()` in one query to detect existence and timestamp, avoiding two separate lookups.
* \*\*Avoid SELECT \***:** Explicitly list needed columns for clarity and to leverage covering indexes.
* **DRY Config Values**: Fetch constants (e.g., `heartbeat_interval_default`) once at the top of procedures.
* **Sort & Limit**: When releasing or acquiring items, push `ORDER BY` and `LIMIT` into the core `UPDATE` or `INSERT` to minimize intermediary data.

---

## 8. Procedure Orchestration

* **Main Orchestrator**:

    * Should only start one transaction and call sub‑procedures in sequence.
    * Avoid nested `START TRANSACTION` in sub‑procedures; let the parent control commit/rollback.
* **Result Sets**:

    * Return a single summarizing `SELECT` (or JSON object) listing `worker_id`, `leases_added`, and `leases_removed` for simplicity.

---

## 9. Example Snippet

```sql
-- Heartbeat upsert with LAST_INSERT_ID
CREATE PROCEDURE sp_workers_heartbeat(
  IN p_node_id VARCHAR(255),
  IN p_interval DOUBLE,
  OUT p_id BIGINT
)
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN ROLLBACK; END;
  START TRANSACTION;

  INSERT INTO workers (node_id, heartbeat_interval, heartbeat_detected_at)
  VALUES (p_node_id, p_interval, UTC_TIMESTAMP())
  ON DUPLICATE KEY UPDATE
    heartbeat_interval = p_interval,
    heartbeat_detected_at = UTC_TIMESTAMP(),
    id = LAST_INSERT_ID(id);

  SET p_id = LAST_INSERT_ID();
  COMMIT;
END;
```

