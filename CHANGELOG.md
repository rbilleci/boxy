# Changelog

All production-readiness work is tracked here in reverse-chronological order.
Each entry maps to a numbered item in [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md).

---

## [Unreleased]

### In Progress
<!-- Items currently being worked on -->

### Completed
| Date | Item # | Description | Commit |
|------|--------|-------------|--------|
| 2026-03-02 | 80-88 | Section 3: Observability — BoxyMeterRegistry, Micrometer timers (publish/commit), HikariCP metrics, BaseRepository SQL logging, logback.xml JSON format, HealthCheck utility, docs/monitoring.md | 1676ac0 |
| 2026-03-02 | 71-79 | Section 2: SP error handling — EXIT HANDLER FOR SQLEXCEPTION on all stored procedures (publish, publish_advanced, publish_multi, sequence, sequence_loop, consumers_gc, subscriptions, topics__delete, poll) | a58b75e |
| 2026-03-02 | 65-70 | Section 1: CI/CD — Maven Release plugin, Dependabot, JaCoCo coverage gates (LINE≥60%/BRANCH≥50%), EditorConfig | ec7db43 |
| 2026-03-02 | 61-64 | Documentation: README performance section, docs/configuration.md, benchmark methodology + PR gate criteria | 27483e6 |
| 2026-03-02 | 58-60 | Consumer GC v2: batched lease cleanup + consumer deletion (LIMIT 100), evaluation docs | 6c3e77a |
| 2026-03-02 | 54-57 | Schema performance: covering indexes, innodb_autoinc_lock_mode=2, unprocessed_events + compression evaluation | d5d41fc |
| 2026-03-02 | 51-53 | Commit path v2: release leases on commit, evaluation docs for temp table vs direct UPDATE | 22a3be3 |
| 2026-03-02 | 43-50 | Poll path v2: adaptive polling_probability, IN p_batch_size param, subscription stats update, evaluation docs | 3ce7aa7 |
| 2026-03-02 | 37-42 | Sequencer: sp_sequence v2 (ROW_NUMBER watermark), dynamic batch sizing, 1ms sleep, partition index | 74e8605 |
| 2026-03-02 | 32-36 | Publish path: sp_events__publish_multi v2 (bulk INSERT), EventRepository.publishBatch, PublishRequest, evaluation docs | 9501300 |
| 2026-03-02 | 28-31 | Benchmark infrastructure: PipelineBenchmarkIT, component benchmarks (sequencer/poll/commit), Maven bench profiles, docs/benchmarks.md | f4c4b58 |
| 2026-03-02 | 16-27 | Implement boxy-cli with picocli + GraalVM native-image support | 425dec5 |
| 2026-03-02 | 11-15 | Restructure into boxy-core/mysql/pgsql/test/cli modules | 93fd685 |
| 2026-03-02 | 10 | Pin Maven plugins for JDK 25; add javadoc + source plugins | 43b52be |
| 2026-03-02 | 9 | Adopt JDK 25 idioms; enhance DataAccessException; add Javadoc | 181c653 |
| 2026-03-02 | 8 | Upgrade assertj → 3.27.5, testcontainers → 1.21.4 for JDK 25 compat | e4b693f |
| 2026-03-02 | 7 | Add GitHub Actions CI workflow; update README prerequisites | af8e1be |
| 2026-03-02 | 6 | Upgrade Maven compiler source/target from Java 21 to 25 | 98b2f54 |
| 2026-03-02 | 5 | Add .cursorrules for Cursor IDE project intelligence | 795880d |
| 2026-03-02 | 4 | Add MCP server configuration (.claude/settings.json + docs/mcp-setup.md) | 7ff6d7b |
| 2026-03-02 | 3 | Add CONTRIBUTING.md for contributor guidelines | 83e9425 |
| 2026-03-02 | 2 | Add AGENTS.md for agent workflow rules | ad3bd46 |
| 2026-03-02 | 1 | Add CLAUDE.md for AI agent context | 4de2c9d |
| 2026-03-02 | Plan | Add PRODUCTION_READINESS.md and CHANGELOG.md | d255029 |

---

## Status Summary

| Phase | Total | Done | Remaining |
|-------|-------|------|-----------|
| Phase 0A: Agent Optimization | 5 | 5 | 0 |
| Phase 0B: JDK 25 Upgrade | 5 | 5 | 0 |
| Phase 0C: Module Restructuring | 5 | 5 | 0 |
| Phase 0D: CLI Implementation | 12 | 12 | 0 |
| Phase 1A: Benchmark Infrastructure | 4 | 4 | 0 |
| Phase 1B: Publish Path | 5 | 5 | 0 |
| Phase 1C: Sequencer | 6 | 6 | 0 |
| Phase 1D: Poll Path | 8 | 8 | 0 |
| Phase 1E: Commit Path | 3 | 3 | 0 |
| Phase 1F: Schema Performance | 4 | 4 | 0 |
| Phase 1G: Consumer GC | 3 | 3 | 0 |
| Phase 1H: Documentation | 4 | 4 | 0 |
| Section 1: CI/CD | 6 | 6 | 0 |
| Section 2: SP Error Handling | 9 | 9 | 0 |
| Section 3: Observability | 9 | 9 | 0 |
| Section 4: Configuration | 6 | 0 | 6 |
| Section 5: Schema & Data | 10 | 0 | 10 |
| Section 6: Security | 5 | 0 | 5 |
| Section 7: Java Resilience | 4 | 0 | 4 |
| Section 8: Testing | 8 | 0 | 8 |
| Section 9: Background Reliability | 8 | 0 | 8 |
| Section 10: PostgreSQL | 8 | 0 | 8 |
| Section 11: Docs & DX | 8 | 0 | 8 |
| Section 12: Packaging | 4 | 0 | 4 |
| Section 13: Branch Hygiene | 4 | 0 | 4 |
| Section 14: Client SDKs | 6 | 0 | 6 |
| Section 15: Core Enhancements | 8 | 0 | 8 |
| **TOTAL** | **156** | **88** | **68** |
