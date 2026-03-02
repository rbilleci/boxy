# MCP Server Setup for Boxy Development

This guide explains how to configure Model Context Protocol (MCP) servers when working on Boxy with AI agents (Claude Code, Cursor, etc.).

MCP servers give AI agents direct access to your local Boxy database, GitHub issues, and structured reasoning tools — enabling more accurate schema introspection, query execution, and issue-driven development.

---

## Prerequisites

- Node.js 18+ (for `npx`)
- A running local MySQL instance with a Boxy schema initialized (`mvn liquibase:update`)
- A GitHub personal access token (for the GitHub MCP server)

---

## Included Servers

### 1. MySQL (`@benborla29/mcp-server-mysql`)

Gives the agent read access to your local `events_db` schema so it can introspect tables, columns, indexes, and stored procedures in real time — without hallucinating schema details.

**Configuration** (`.claude/settings.json`):
```json
"mysql": {
  "command": "npx",
  "args": ["-y", "@benborla29/mcp-server-mysql"],
  "env": {
    "MYSQL_HOST": "127.0.0.1",
    "MYSQL_PORT": "3306",
    "MYSQL_USER": "user",
    "MYSQL_PASS": "password",
    "MYSQL_DB": "events_db"
  }
}
```

**To start a local database for the agent:**
```bash
# Option A: use Testcontainers (started automatically by mvn test)
# The agent cannot connect here since Testcontainers uses ephemeral ports

# Option B: start MySQL via Docker manually
docker run -d \
  --name boxy-dev \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=events_db \
  -e MYSQL_USER=user \
  -e MYSQL_PASSWORD=password \
  -p 3306:3306 \
  mysql:8.0 \
  --event-scheduler=ON

# Apply migrations
mvn liquibase:update \
  -Dliquibase.url=jdbc:mysql://localhost:3306/events_db \
  -Dliquibase.username=user \
  -Dliquibase.password=password
```

**Customize credentials** by editing `.claude/settings.json` or by setting environment variables before launching your agent.

---

### 2. GitHub (`@modelcontextprotocol/server-github`)

Gives the agent access to Boxy's GitHub issues, pull requests, and repository metadata. This enables issue-driven development (referencing issue numbers in commits, looking up requirements directly).

**Setup:**
1. Create a personal access token at https://github.com/settings/tokens
2. Required scopes: `repo` (read)
3. Set the token as an environment variable before starting your agent:
   ```bash
   export GITHUB_TOKEN=ghp_your_token_here
   ```

The `.claude/settings.json` configuration references `${GITHUB_TOKEN}` — most MCP clients expand this automatically. If yours does not, replace the placeholder with your token directly (and do not commit it).

---

### 3. Sequential Thinking (`@modelcontextprotocol/server-sequential-thinking`)

Provides structured, multi-step reasoning capability. Useful for complex architectural analysis or when the agent needs to reason through a stored procedure optimization before proposing code changes.

No configuration required beyond the entry in `settings.json`.

---

## Using a Custom MySQL Password

If your local Boxy database uses different credentials, override them without editing the committed file:

```bash
# Set environment variables before starting Claude Code
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_USER=myuser
export MYSQL_PASS=mypassword
export MYSQL_DB=events_db
```

Alternatively, create a `.claude/settings.local.json` (gitignored) and your MCP client may pick it up — check your specific client's documentation.

---

## Security Notes

- **Never commit credentials** to `.claude/settings.json`. The file uses `${ENV_VAR}` placeholders or default dev credentials intentionally.
- The MySQL MCP server should only be pointed at a **local development database**, never a production instance.
- The `.claude/` directory is not gitignored by default — be careful what you add there. The current `settings.json` contains no secrets.

---

## Verifying MCP Servers Are Active

When using Claude Code, you can verify active MCP servers with:
```bash
claude mcp list
```

Or within an agent session, ask: "What MCP servers are available to you?"

---

## Troubleshooting

**`npx: command not found`** — install Node.js 18+ from https://nodejs.org

**MySQL connection refused** — ensure your local database is running on port 3306 and the schema has been initialized with `mvn liquibase:update`

**GitHub authentication error** — verify your token has `repo` scope and has not expired

**Agent doesn't see the MCP servers** — confirm you launched the agent from the project root (where `.claude/settings.json` lives) and that your client version supports project-level MCP configuration
