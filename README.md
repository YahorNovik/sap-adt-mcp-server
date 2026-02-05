# SAP ADT MCP Server

An Eclipse plugin that connects Claude Code to your SAP system. It runs an MCP (Model Context Protocol) server inside Eclipse, giving Claude Code full access to your ABAP codebase — search, read, write, test, and activate objects, all from the command line.

## What It Does

You install this plugin in Eclipse, connect it to your SAP system, and start the MCP server. Then Claude Code can talk directly to SAP — read your ABAP classes, modify source code, run unit tests, check syntax, create new objects, and more. No copy-pasting code back and forth.

## Quick Start

### 1. Install the Plugin

Open Eclipse and go to **Help → Install New Software...**

Click **Add...** and enter:
- **Name:** `SAP ADT MCP Server`
- **Location:** `https://yahornovik.github.io/sap-adt-mcp-server/`

Check **SAP ADT MCP Server**, click **Next → Finish**, and restart Eclipse.

### 2. Open the View

Go to **Window → Show View → Other...**, find **SAP ADT MCP Server** and open it.

You'll see a panel with four buttons: **Connect SAP**, **Start Server**, **Launch Claude Code**, and **Clear**.

### 3. Connect to SAP

Click **Connect SAP**. A dialog appears — fill in:

| Field | Example |
|-------|---------|
| SAP URL | `https://your-sap-host:44300` |
| User | `DEVELOPER` |
| Password | `********` |
| Client | `100` |
| Language | `EN` |

The plugin logs into SAP via ADT REST APIs and registers all 15 tools. You'll see a confirmation message in the output area.

### 4. Start the MCP Server

Click **Start Server**. This starts a local HTTP server on port 3000 that speaks the MCP protocol. The plugin also automatically writes the MCP configuration to `~/.claude/mcp_servers.json` — if you already have other MCP servers configured (like sap-docs), they are preserved.

### 5. Launch Claude Code

You have two options:

**Option A — Click "Launch Claude Code"**
This opens the Eclipse Terminal view (or your system terminal) so you can run Claude right inside your IDE.

**Option B — Run from any terminal**
```bash
claude
```
The MCP config was already written in step 4, so Claude Code automatically picks up the SAP server. No extra flags needed.

## What You Can Do

Once connected, Claude Code has access to these SAP tools:

### Reading and Searching

| Tool | What it does |
|------|-------------|
| `sap_search` | Search for ABAP objects by name pattern (e.g., "ZCL_*", "Z*SALES*") |
| `sap_get_source` | Read the source code of any program, class, interface, or function module |
| `sap_object_structure` | Get metadata — package, description, includes, methods, etc. |
| `sap_usage_references` | Find where an object is used (where-used list) |
| `sap_inactive_objects` | List all inactive objects for the current user |
| `sap_abap_docu` | Look up ABAP keyword documentation, class docs, function module docs |

### Writing and Modifying

| Tool | What it does |
|------|-------------|
| `sap_create_object` | Create new programs, classes, interfaces, or function groups |
| `sap_lock` | Lock an object for editing |
| `sap_set_source` | Update the source code (handles lock, write, unlock, activate) |
| `sap_unlock` | Release an object lock |
| `sap_activate` | Activate objects to make changes effective |

### Testing and Quality

| Tool | What it does |
|------|-------------|
| `sap_syntax_check` | Check syntax of an object |
| `sap_run_unit_tests` | Run ABAP Unit tests and return results |
| `sap_atc_run` | Run ATC code quality checks |
| `sap_sql_query` | Execute SQL queries against the database |

### Example Prompts

Once Claude Code is running with the SAP server:

```
> Search for all classes that start with ZCL_SALES

> Show me the source code of class ZCL_SALES_ORDER

> Create a new class ZCL_MY_HELPER in package $TMP with description "Utility class"

> Run unit tests for ZCL_SALES_ORDER

> Fix the syntax error in program ZSALES_REPORT and activate it

> Run ATC checks on ZCL_SALES_ORDER and fix any findings

> Find all programs that use function module BAPI_SALESORDER_CREATEFROMDAT2
```

Claude reads the code, makes changes, runs checks, and activates — all through the MCP tools.

## Adding SAP Documentation Search

For even better results, add the [mcp-sap-docs](https://github.com/marianfoo/mcp-sap-docs) server. It gives Claude access to SAP Help Portal, SAP Community posts, and ABAP language reference — so it can look up documentation while coding.

The hosted version requires no setup. Just add it to your `~/.claude/mcp_servers.json`:

```json
{
  "sap-adt": {
    "url": "http://localhost:3000/mcp"
  },
  "sap-docs": {
    "url": "https://mcp-sap-docs.marianzeis.de/mcp"
  }
}
```

Now Claude can both work with your SAP system **and** search official SAP documentation.

## Building from Source

```bash
git clone https://github.com/YahorNovik/sap-adt-mcp-server.git
cd sap-adt-mcp-server
mvn clean verify
```

The P2 update site is generated in `com.sap.adt.mcp.server.site/target/repository/`. Install from Eclipse: **Help → Install New Software... → Add... → Local...** and point to that directory.

## Requirements

- Eclipse 2024-03 or newer (with ADT installed)
- Java 17+
- SAP system with ADT services enabled (`/sap/bc/adt` ICF nodes active)
- Claude Code CLI installed (`npm install -g @anthropic-ai/claude-code`)

## License

Apache 2.0
