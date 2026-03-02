const fs = require("fs");
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        Header, Footer, AlignmentType, LevelFormat,
        HeadingLevel, BorderStyle, WidthType, ShadingType,
        PageNumber, PageBreak } = require("docx");

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 80, bottom: 80, left: 120, right: 120 };

function headerCell(text, width) {
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: "1B3A5C", type: ShadingType.CLEAR },
    margins: cellMargins,
    verticalAlign: "center",
    children: [new Paragraph({ children: [new TextRun({ text, bold: true, font: "Arial", size: 20, color: "FFFFFF" })] })]
  });
}

function cell(text, width, opts = {}) {
  const fill = opts.fill || undefined;
  const shading = fill ? { fill, type: ShadingType.CLEAR } : undefined;
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading,
    margins: cellMargins,
    children: [new Paragraph({ children: [new TextRun({ text, font: "Arial", size: 20, bold: opts.bold || false, color: opts.color || "333333" })] })]
  });
}

function severityCell(severity, width) {
  const colors = {
    "CRITICAL": { fill: "FADBD8", color: "922B21" },
    "HIGH": { fill: "FDEBD0", color: "935116" },
    "MEDIUM": { fill: "FEF9E7", color: "7D6608" },
    "LOW": { fill: "D5F5E3", color: "1E8449" }
  };
  const c = colors[severity] || { fill: "FFFFFF", color: "333333" };
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: c.fill, type: ShadingType.CLEAR },
    margins: cellMargins,
    children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: severity, bold: true, font: "Arial", size: 20, color: c.color })] })]
  });
}

// Build items
const items = [
  // 1. CI/CD & Build Infrastructure
  { cat: "CI/CD & Build Infrastructure", sev: "CRITICAL", item: "Create GitHub Actions CI pipeline (build, test, lint on every push/PR)" },
  { cat: "CI/CD & Build Infrastructure", sev: "CRITICAL", item: "Add integration test job using Testcontainers with MySQL in CI" },
  { cat: "CI/CD & Build Infrastructure", sev: "HIGH", item: "Configure Maven release plugin and publish to Maven Central or GitHub Packages" },
  { cat: "CI/CD & Build Infrastructure", sev: "HIGH", item: "Add dependency vulnerability scanning (Dependabot or Snyk)" },
  { cat: "CI/CD & Build Infrastructure", sev: "MEDIUM", item: "Add code coverage reporting (JaCoCo) with minimum threshold enforcement" },
  { cat: "CI/CD & Build Infrastructure", sev: "LOW", item: "Add .editorconfig for consistent code style across contributors" },

  // 2. Stored Procedure Error Handling
  { cat: "Stored Procedure Error Handling", sev: "CRITICAL", item: "Add EXIT HANDLER with ROLLBACK to sp_events__publish.sql (core publish path is unprotected)" },
  { cat: "Stored Procedure Error Handling", sev: "CRITICAL", item: "Add EXIT HANDLER with ROLLBACK to sp_events__publish_advanced.sql" },
  { cat: "Stored Procedure Error Handling", sev: "CRITICAL", item: "Add EXIT HANDLER with ROLLBACK to sp_events__publish_multi.sql (loops through JSON without per-item error handling)" },
  { cat: "Stored Procedure Error Handling", sev: "CRITICAL", item: "Add EXIT HANDLER to sp_sequence.sql (batch sequencing path has no rollback strategy)" },
  { cat: "Stored Procedure Error Handling", sev: "HIGH", item: "Add EXIT HANDLER to sp_sequence_loop.sql (loops until timeout with no error recovery)" },
  { cat: "Stored Procedure Error Handling", sev: "HIGH", item: "Add EXIT HANDLER to sp_consumers__gc.sql (silent failure leaves dead consumers blocking partitions)" },
  { cat: "Stored Procedure Error Handling", sev: "MEDIUM", item: "Add error handlers to sp_subscriptions__create, sp_subscriptions__delete, sp_subscriptions__subscribe, sp_subscriptions__unsubscribe" },
  { cat: "Stored Procedure Error Handling", sev: "MEDIUM", item: "Add error handler to sp_topics__delete.sql" },
  { cat: "Stored Procedure Error Handling", sev: "MEDIUM", item: "Add error handler to sp_events__poll.sql (complex procedure with no top-level handler)" },

  // 3. Observability & Monitoring
  { cat: "Observability & Monitoring", sev: "CRITICAL", item: "Add Micrometer metrics integration for publish latency, poll latency, commit latency, and throughput" },
  { cat: "Observability & Monitoring", sev: "CRITICAL", item: "Instrument event processing lag (high_watermark minus cursor position per subscription)" },
  { cat: "Observability & Monitoring", sev: "HIGH", item: "Add structured logging via logback.xml with JSON formatter for production environments" },
  { cat: "Observability & Monitoring", sev: "HIGH", item: "Add logging statements to all repository methods (currently zero logging in main code)" },
  { cat: "Observability & Monitoring", sev: "HIGH", item: "Add metrics for sequencer execution (events processed per run, duration, failures)" },
  { cat: "Observability & Monitoring", sev: "HIGH", item: "Add metrics for consumer_gc execution (consumers reaped per run, duration)" },
  { cat: "Observability & Monitoring", sev: "MEDIUM", item: "Expose HikariCP connection pool metrics via Micrometer (connections active, idle, pending)" },
  { cat: "Observability & Monitoring", sev: "MEDIUM", item: "Add health check endpoint or utility for database connectivity and sequencer liveness" },
  { cat: "Observability & Monitoring", sev: "LOW", item: "Document recommended Grafana dashboards or alerting thresholds" },

  // 4. Configuration Externalization
  { cat: "Configuration Externalization", sev: "HIGH", item: "Make HikariCP pool size configurable via environment variable (currently hardcoded to 10)" },
  { cat: "Configuration Externalization", sev: "HIGH", item: "Make consumer lease lock duration configurable (currently hardcoded to 3 seconds in sp_events__poll.sql)" },
  { cat: "Configuration Externalization", sev: "HIGH", item: "Make polling batch size configurable (currently hardcoded to 100 in sp_events__poll.sql)" },
  { cat: "Configuration Externalization", sev: "HIGH", item: "Make sequencer batch size configurable (currently hardcoded to 1000 in sequencer event)" },
  { cat: "Configuration Externalization", sev: "MEDIUM", item: "Make default heartbeat_interval configurable (currently 15.0s in subscription_topics default)" },
  { cat: "Configuration Externalization", sev: "MEDIUM", item: "Compute polling_probability dynamically from subscription statistics (currently hardcoded to 0.001)" },

  // 5. Schema & Data Management
  { cat: "Schema & Data Management", sev: "HIGH", item: "Add created_at timestamp column to events table for time-based retention queries" },
  { cat: "Schema & Data Management", sev: "HIGH", item: "Add index on events.created_at to support efficient cleanup without full table scans" },
  { cat: "Schema & Data Management", sev: "HIGH", item: "Document and provide example retention/cleanup stored procedures or scripts" },
  { cat: "Schema & Data Management", sev: "MEDIUM", item: "Add index on unprocessed_events.partition_id for efficient sequencer lookups" },
  { cat: "Schema & Data Management", sev: "MEDIUM", item: "Add maximum event payload size validation in publish stored procedures" },
  { cat: "Schema & Data Management", sev: "MEDIUM", item: "Add maximum array length validation in sp_events__publish_multi.sql" },
  { cat: "Schema & Data Management", sev: "LOW", item: "Consider adding event_type or content_type metadata column to events table" },

  // 6. Security & Input Validation
  { cat: "Security & Input Validation", sev: "HIGH", item: "Replace manual JSON construction in ConsumerRepository.toJsonArray() with a JSON library" },
  { cat: "Security & Input Validation", sev: "HIGH", item: "Replace manual JSON construction in CursorRepository.toJsonObject() with a JSON library" },
  { cat: "Security & Input Validation", sev: "MEDIUM", item: "Add input length validation for namespace names (enforced at Java layer, not just DB constraints)" },
  { cat: "Security & Input Validation", sev: "MEDIUM", item: "Add input validation for topic partition counts (positive, within documented limits)" },
  { cat: "Security & Input Validation", sev: "LOW", item: "Add namespace/topic name character validation (reject control characters, null bytes)" },

  // 7. Java Core Resilience
  { cat: "Java Core Resilience", sev: "HIGH", item: "Distinguish retryable errors (connection loss, lock timeout) from fatal errors (constraint violation) in DataAccessException" },
  { cat: "Java Core Resilience", sev: "HIGH", item: "Add retry logic with exponential backoff for transient database failures in repository layer" },
  { cat: "Java Core Resilience", sev: "MEDIUM", item: "Add circuit breaker pattern for stored procedure calls to prevent cascade failures" },
  { cat: "Java Core Resilience", sev: "MEDIUM", item: "Enrich DataAccessException with context (repository name, operation, parameters)" },

  // 8. Testing
  { cat: "Testing", sev: "HIGH", item: "Add error/edge-case integration tests (duplicate consumers, invalid topics, stale commits, unknown consumers)" },
  { cat: "Testing", sev: "HIGH", item: "Replace Thread.sleep(1000) sequencer waits with polling/retry-based assertion (fragile in slow CI environments)" },
  { cat: "Testing", sev: "HIGH", item: "Fix PostgreSQL teardown bug in BaseIT (tables list is created empty, never populated from ResultSet)" },
  { cat: "Testing", sev: "MEDIUM", item: "Add concurrent consumer registration/polling tests to verify lease correctness under contention" },
  { cat: "Testing", sev: "MEDIUM", item: "Add event ordering guarantee tests across partitions" },
  { cat: "Testing", sev: "MEDIUM", item: "Add consumer lease expiration and rebalancing tests" },
  { cat: "Testing", sev: "MEDIUM", item: "Add unit tests for mapper classes and JSON construction utilities" },
  { cat: "Testing", sev: "LOW", item: "Add large-scale stress tests (thousands of topics/partitions, hundreds of consumers)" },

  // 9. PostgreSQL Support
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Port all 16 stored procedures from MySQL to PL/pgSQL" },
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Port all 5 functions to PostgreSQL" },
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Create PostgreSQL schema DDL (data types, generated columns, MEMORY engine alternatives)" },
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Port sequencer and consumer_gc from MySQL events to pg_cron or application-level scheduling" },
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Create PostgreSQL Liquibase changelog files" },
  { cat: "PostgreSQL Support", sev: "MEDIUM", item: "Add PostgreSQL Testcontainers integration test profile" },
  { cat: "PostgreSQL Support", sev: "LOW", item: "Alternatively: remove PostgreSQL references from README and DataSourceProvider if not planned" },

  // 10. Documentation & Developer Experience
  { cat: "Documentation & Developer Experience", sev: "HIGH", item: "Write an operational runbook (scaling, monitoring, troubleshooting, maintenance)" },
  { cat: "Documentation & Developer Experience", sev: "HIGH", item: "Document event retention strategy and provide cleanup procedure examples" },
  { cat: "Documentation & Developer Experience", sev: "HIGH", item: "Write a configuration tuning guide (pool sizes, batch sizes, heartbeat intervals)" },
  { cat: "Documentation & Developer Experience", sev: "MEDIUM", item: "Add Javadoc to all public classes, methods, and domain records" },
  { cat: "Documentation & Developer Experience", sev: "MEDIUM", item: "Add inline documentation to all stored procedures (parameters, behavior, error codes)" },
  { cat: "Documentation & Developer Experience", sev: "MEDIUM", item: "Publish a quickstart example project demonstrating producer and consumer integration" },
  { cat: "Documentation & Developer Experience", sev: "LOW", item: "Add architecture decision records (ADRs) for key design choices" },
  { cat: "Documentation & Developer Experience", sev: "LOW", item: "Create a CHANGELOG.md for tracking version history" },

  // 11. Packaging & Distribution
  { cat: "Packaging & Distribution", sev: "HIGH", item: "Configure Maven Central or GitHub Packages publishing for boxy-core and boxy-db JARs" },
  { cat: "Packaging & Distribution", sev: "HIGH", item: "Add proper Maven metadata (description, URL, SCM, developers, license) to POMs" },
  { cat: "Packaging & Distribution", sev: "MEDIUM", item: "Build and publish a reference client SDK (at minimum the Java client with a clean public API)" },
  { cat: "Packaging & Distribution", sev: "MEDIUM", item: "Version the project properly (remove -SNAPSHOT for release, set up semantic versioning)" },
  { cat: "Packaging & Distribution", sev: "LOW", item: "Consider publishing a Docker image with pre-configured MySQL and Boxy schema for evaluation" },

  // 12. Background Event Reliability
  { cat: "Background Event Reliability", sev: "CRITICAL", item: "Add error logging/alerting mechanism for sequencer event failures (currently silent)" },
  { cat: "Background Event Reliability", sev: "CRITICAL", item: "Add error logging/alerting mechanism for consumer_gc event failures (currently silent)" },
  { cat: "Background Event Reliability", sev: "HIGH", item: "Add a liveness check or monitoring query for sequencer (detect stalled sequencing)" },
  { cat: "Background Event Reliability", sev: "HIGH", item: "Add a liveness check for consumer_gc (detect accumulation of dead consumers)" },
  { cat: "Background Event Reliability", sev: "MEDIUM", item: "Consider making sequencer interval configurable (currently hardcoded 1 MINUTE)" },
  { cat: "Background Event Reliability", sev: "MEDIUM", item: "Consider making consumer_gc interval configurable (currently hardcoded 1 MINUTE)" },

  // 13. Branch & Release Hygiene
  { cat: "Branch & Release Hygiene", sev: "HIGH", item: "Merge current refactor branch into main and establish main as the primary branch" },
  { cat: "Branch & Release Hygiene", sev: "MEDIUM", item: "Clean up ~30 stale local branches (codex/*, jetbrains-junie/*)" },
  { cat: "Branch & Release Hygiene", sev: "MEDIUM", item: "Establish branch protection rules on main (require PR reviews, passing CI)" },
  { cat: "Branch & Release Hygiene", sev: "LOW", item: "Tag releases and maintain a release process (GitHub Releases with changelogs)" },
];

// Group by category
const categories = [];
const catMap = {};
for (const item of items) {
  if (!catMap[item.cat]) {
    catMap[item.cat] = [];
    categories.push(item.cat);
  }
  catMap[item.cat].push(item);
}

// Build document children
const children = [];

// Title
children.push(new Paragraph({ spacing: { after: 100 }, children: [new TextRun({ text: "BOXY", font: "Arial", size: 28, bold: true, color: "7F8C8D" })] }));
children.push(new Paragraph({ spacing: { after: 400 }, children: [new TextRun({ text: "Production Readiness Checklist", font: "Arial", size: 52, bold: true, color: "1B3A5C" })] }));
children.push(new Paragraph({ spacing: { after: 200 }, children: [new TextRun({ text: "A comprehensive inventory of work items required to bring the Boxy event streaming library from its current development state to production readiness. Items are organized by category and prioritized by severity.", font: "Arial", size: 22, color: "555555" })] }));

// Summary counts
const critCount = items.filter(i => i.sev === "CRITICAL").length;
const highCount = items.filter(i => i.sev === "HIGH").length;
const medCount = items.filter(i => i.sev === "MEDIUM").length;
const lowCount = items.filter(i => i.sev === "LOW").length;

children.push(new Paragraph({ spacing: { before: 200, after: 100 }, children: [new TextRun({ text: `${items.length} total items:  ${critCount} Critical  |  ${highCount} High  |  ${medCount} Medium  |  ${lowCount} Low`, font: "Arial", size: 22, bold: true, color: "1B3A5C" })] }));
children.push(new Paragraph({ children: [new PageBreak()] }));

// Summary table
children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 200, after: 200 }, children: [new TextRun({ text: "Summary by Category", font: "Arial", size: 32, bold: true, color: "1B3A5C" })] }));

const summaryRows = [
  new TableRow({ children: [
    headerCell("Category", 4000),
    headerCell("Critical", 1200),
    headerCell("High", 1200),
    headerCell("Medium", 1200),
    headerCell("Low", 1200),
    headerCell("Total", 960),
  ]})
];

for (const cat of categories) {
  const catItems = catMap[cat];
  const c = catItems.filter(i => i.sev === "CRITICAL").length;
  const h = catItems.filter(i => i.sev === "HIGH").length;
  const m = catItems.filter(i => i.sev === "MEDIUM").length;
  const l = catItems.filter(i => i.sev === "LOW").length;
  summaryRows.push(new TableRow({ children: [
    cell(cat, 4000, { bold: true }),
    cell(c ? String(c) : "-", 1200),
    cell(h ? String(h) : "-", 1200),
    cell(m ? String(m) : "-", 1200),
    cell(l ? String(l) : "-", 1200),
    cell(String(catItems.length), 960, { bold: true }),
  ]}));
}

summaryRows.push(new TableRow({ children: [
  cell("TOTAL", 4000, { bold: true, fill: "EBF5FB" }),
  cell(String(critCount), 1200, { bold: true, fill: "EBF5FB" }),
  cell(String(highCount), 1200, { bold: true, fill: "EBF5FB" }),
  cell(String(medCount), 1200, { bold: true, fill: "EBF5FB" }),
  cell(String(lowCount), 1200, { bold: true, fill: "EBF5FB" }),
  cell(String(items.length), 960, { bold: true, fill: "EBF5FB" }),
]}));

children.push(new Table({
  width: { size: 9760, type: WidthType.DXA },
  columnWidths: [4000, 1200, 1200, 1200, 1200, 960],
  rows: summaryRows,
}));

children.push(new Paragraph({ children: [new PageBreak()] }));

// Detail sections
let itemNum = 1;
for (const cat of categories) {
  children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 300, after: 200 }, children: [new TextRun({ text: cat, font: "Arial", size: 30, bold: true, color: "1B3A5C" })] }));

  const rows = [
    new TableRow({ children: [
      headerCell("#", 500),
      headerCell("Severity", 1100),
      headerCell("Item", 8160),
    ]})
  ];

  for (const item of catMap[cat]) {
    const rowFill = itemNum % 2 === 0 ? "F8F9FA" : undefined;
    rows.push(new TableRow({ children: [
      cell(String(itemNum), 500, { fill: rowFill }),
      severityCell(item.sev, 1100),
      cell(item.item, 8160, { fill: rowFill }),
    ]}));
    itemNum++;
  }

  children.push(new Table({
    width: { size: 9760, type: WidthType.DXA },
    columnWidths: [500, 1100, 8160],
    rows,
  }));

  children.push(new Paragraph({ spacing: { after: 200 }, children: [] }));
}

const doc = new Document({
  styles: {
    default: { document: { run: { font: "Arial", size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: "Arial", color: "1B3A5C" },
        paragraph: { spacing: { before: 300, after: 200 }, outlineLevel: 0 } },
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1200, right: 1200, bottom: 1200, left: 1200 }
      }
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: "Boxy \u2014 Production Readiness Checklist", font: "Arial", size: 16, color: "999999", italics: true })]
      })] })
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [new TextRun({ text: "Page ", font: "Arial", size: 16, color: "999999" }), new TextRun({ children: [PageNumber.CURRENT], font: "Arial", size: 16, color: "999999" })]
      })] })
    },
    children,
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync("/sessions/friendly-magical-goldberg/mnt/boxy/boxy-production-readiness.docx", buffer);
  console.log("Document created successfully: " + items.length + " items across " + categories.length + " categories");
});
