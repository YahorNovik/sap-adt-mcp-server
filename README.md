# SAP ADT MCP Server

Eclipse plugin that exposes SAP ADT (ABAP Development Tools) via the MCP (Model Context Protocol), enabling AI assistants like Claude Code to interact with SAP systems.

## Installation

### From GitHub Pages (Recommended)

1. Open Eclipse
2. Go to **Help → Install New Software...**
3. Click **Add...**
4. Enter:
   - Name: `SAP ADT MCP Server`
   - Location: `https://yahornovik.github.io/sap-adt-mcp-server/`
5. Click **Add**
6. Check **SAP ADT MCP Server**
7. Click **Next → Next → Finish**
8. Restart Eclipse

### From Local Build

```bash
git clone https://github.com/YahorNovik/sap-adt-mcp-server.git
cd sap-adt-mcp-server
mvn clean verify
```

Then in Eclipse: **Help → Install New Software... → Add... → Local...** and select:
```
<project>/com.sap.adt.mcp.server.site/target/repository
```

## Usage

### 1. Open the MCP Server View

**Window → Show View → Other... → SAP → SAP ADT MCP Server**

### 2. Connect to SAP

Click **Connect SAP** and enter:
- SAP URL (e.g., `https://your-sap-host:44300`)
- Username
- Password
- Client (e.g., `100`)
- Language (e.g., `EN`)

### 3. Start the MCP Server

Click **Start Server** - this starts the MCP server on port 3000.

### 4. Use with Claude Code

```bash
claude --mcp-server http://localhost:3000/mcp
```

Or add to your `~/.claude/mcp_servers.json`:
```json
{
  "sap-adt": {
    "url": "http://localhost:3000/mcp"
  }
}
```

## Available Tools

| Tool | Description |
|------|-------------|
| `sap_search` | Search ABAP objects by name pattern |
| `sap_get_source` | Read source code of ABAP objects |
| `sap_set_source` | Write/update source code |
| `sap_object_structure` | Get object metadata and structure |
| `sap_lock` | Lock an object for editing |
| `sap_unlock` | Release object lock |
| `sap_syntax_check` | Run syntax check on an object |
| `sap_activate` | Activate ABAP objects |
| `sap_inactive_objects` | List inactive objects for current user |
| `sap_create_object` | Create new programs, classes, interfaces, function groups |
| `sap_run_unit_tests` | Run ABAP Unit tests |
| `sap_atc_run` | Run ATC quality checks |
| `sap_usage_references` | Find where-used list |
| `sap_sql_query` | Execute SQL queries |
| `sap_abap_docu` | Look up ABAP documentation |

## Using with SAP Documentation MCP

For best results, combine this plugin with [mcp-sap-docs](https://github.com/marianfoo/mcp-sap-docs) which provides SAP documentation search:

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

This gives Claude Code access to both:
- **sap-adt**: Live SAP system operations (read/write code, run tests, etc.)
- **sap-docs**: SAP documentation search (Help Portal, Community, ABAP reference)

## Requirements

- Eclipse 2024-03 or newer
- Java 17 or newer
- SAP system with ADT services enabled

## Building

```bash
mvn clean verify
```

The P2 update site will be generated in `com.sap.adt.mcp.server.site/target/repository/`.

## License

Apache 2.0
