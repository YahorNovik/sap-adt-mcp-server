package com.sap.adt.mcp.tools;

import com.google.gson.JsonObject;

/**
 * Interface for tools exposed via the MCP server.
 *
 * <p>Each tool provides a name, description, input schema, and execution logic.
 * Tools are called by Claude Code through the MCP protocol.</p>
 */
public interface McpTool {

    /**
     * Returns the unique name of this tool.
     * This is used by Claude Code to invoke the tool.
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     * Claude Code uses this to decide when to use the tool.
     */
    String getDescription();

    /**
     * Returns the JSON Schema for the tool's input parameters.
     */
    JsonObject getInputSchema();

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the input parameters as a JSON object
     * @return the result as a string (can be JSON or plain text)
     * @throws Exception if execution fails
     */
    String execute(JsonObject arguments) throws Exception;
}
