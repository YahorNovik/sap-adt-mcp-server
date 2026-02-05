package com.sap.adt.mcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.tools.McpTool;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * MCP (Model Context Protocol) Server implementing Streamable HTTP transport.
 *
 * <p>Supports the 2024-11-05 MCP protocol over HTTP:</p>
 * <ul>
 *   <li>POST /mcp — JSON-RPC requests (initialize, tools/list, tools/call)</li>
 *   <li>GET /mcp — SSE stream for server-to-client notifications</li>
 *   <li>DELETE /mcp — Close session</li>
 * </ul>
 */
public class McpServer {

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final Gson GSON = new Gson();

    private HttpServer server;
    private final int port;
    private final List<McpTool> tools = new ArrayList<>();
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private ServerStatusListener statusListener;

    public interface ServerStatusListener {
        void onStatusChanged(boolean running, String message);
    }

    public McpServer(int port) {
        this.port = port;
    }

    public void setStatusListener(ServerStatusListener listener) {
        this.statusListener = listener;
    }

    public void registerTool(McpTool tool) {
        tools.add(tool);
    }

    public void registerTools(List<McpTool> toolList) {
        tools.addAll(toolList);
    }

    public void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/mcp", new McpHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        running = true;
        notifyStatus(true, "MCP Server running on port " + port);
        System.out.println("MCP Server started on port " + port);
    }

    public void stop() {
        if (!running || server == null) {
            return;
        }

        server.stop(0);
        sessions.clear();
        running = false;
        notifyStatus(false, "MCP Server stopped");
        System.out.println("MCP Server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public int getToolCount() {
        return tools.size();
    }

    private void notifyStatus(boolean running, String message) {
        if (statusListener != null) {
            statusListener.onStatusChanged(running, message);
        }
    }

    /**
     * Main MCP Streamable HTTP handler.
     */
    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
                    "Content-Type, Accept, Mcp-Session-Id");
            exchange.getResponseHeaders().add("Access-Control-Expose-Headers", "Mcp-Session-Id");

            String method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            switch (method) {
                case "POST":
                    handlePost(exchange);
                    break;
                case "GET":
                    handleGet(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendError(exchange, 405, "Method not allowed");
            }
        }

        /**
         * POST /mcp — Handle JSON-RPC requests.
         * Responds with application/json or text/event-stream based on Accept header.
         */
        private void handlePost(HttpExchange exchange) throws IOException {
            try {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                System.out.println("MCP Request: " + body);

                JsonObject request = JsonParser.parseString(body).getAsJsonObject();
                String rpcMethod = request.has("method") ? request.get("method").getAsString() : "";

                // Handle initialize — create session
                String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");

                if ("initialize".equals(rpcMethod)) {
                    sessionId = UUID.randomUUID().toString();
                    sessions.put(sessionId, true);
                }

                JsonObject response = handleJsonRpc(request);

                String responseStr = GSON.toJson(response);
                System.out.println("MCP Response: " + responseStr);

                byte[] responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (sessionId != null) {
                    exchange.getResponseHeaders().add("Mcp-Session-Id", sessionId);
                }
                exchange.sendResponseHeaders(200, responseBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();

            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        /**
         * GET /mcp — SSE endpoint for server-to-client messages.
         * Claude Code uses this to verify the server is alive.
         */
        private void handleGet(HttpExchange exchange) throws IOException {
            String accept = exchange.getRequestHeaders().getFirst("Accept");

            if (accept != null && accept.contains("text/event-stream")) {
                // SSE stream — keep connection open
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.getResponseHeaders().add("Connection", "keep-alive");

                String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                if (sessionId != null) {
                    exchange.getResponseHeaders().add("Mcp-Session-Id", sessionId);
                }

                exchange.sendResponseHeaders(200, 0);

                // Send an initial comment to keep connection alive
                OutputStream os = exchange.getResponseBody();
                os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                // Keep stream open — Claude Code will close when done
                // This thread will block until the client disconnects
                try {
                    while (running) {
                        Thread.sleep(15000);
                        os.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                } catch (Exception e) {
                    // Client disconnected or server stopped
                }
                try { os.close(); } catch (Exception e) { /* ignore */ }
            } else {
                // Regular GET — return server info as JSON
                JsonObject info = new JsonObject();
                info.addProperty("name", "sap-adt-mcp-server");
                info.addProperty("version", "1.0.0");
                info.addProperty("protocol", MCP_PROTOCOL_VERSION);
                info.addProperty("tools", tools.size());
                info.addProperty("status", "running");

                byte[] responseBytes = GSON.toJson(info).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            }
        }

        /**
         * DELETE /mcp — Close a session.
         */
        private void handleDelete(HttpExchange exchange) throws IOException {
            String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            exchange.sendResponseHeaders(200, -1);
        }

        private JsonObject handleJsonRpc(JsonObject request) {
            String method = request.has("method") ? request.get("method").getAsString() : "";
            JsonElement idElement = request.get("id");

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (idElement != null) {
                response.add("id", idElement);
            }

            try {
                JsonElement paramsElement = request.get("params");
                JsonObject params = (paramsElement != null && paramsElement.isJsonObject())
                        ? paramsElement.getAsJsonObject()
                        : new JsonObject();
                JsonObject result = dispatchMethod(method, params);
                response.add("result", result);
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("code", -32000);
                error.addProperty("message", e.getMessage());
                response.add("error", error);
            }

            return response;
        }

        private JsonObject dispatchMethod(String method, JsonObject params) throws Exception {
            switch (method) {
                case "initialize":
                    return handleInitialize(params);
                case "tools/list":
                    return handleToolsList();
                case "tools/call":
                    return handleToolsCall(params);
                case "notifications/initialized":
                    return new JsonObject();
                case "ping":
                    return new JsonObject();
                default:
                    throw new Exception("Unknown method: " + method);
            }
        }

        private JsonObject handleInitialize(JsonObject params) {
            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);

            JsonObject capabilities = new JsonObject();
            JsonObject toolsCap = new JsonObject();
            toolsCap.addProperty("listChanged", false);
            capabilities.add("tools", toolsCap);
            result.add("capabilities", capabilities);

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("name", "sap-adt-mcp-server");
            serverInfo.addProperty("version", "1.0.0");
            result.add("serverInfo", serverInfo);

            return result;
        }

        private JsonObject handleToolsList() {
            JsonObject result = new JsonObject();
            JsonArray toolsArray = new JsonArray();

            for (McpTool tool : tools) {
                JsonObject toolDef = new JsonObject();
                toolDef.addProperty("name", tool.getName());
                toolDef.addProperty("description", tool.getDescription());
                toolDef.add("inputSchema", tool.getInputSchema());
                toolsArray.add(toolDef);
            }

            result.add("tools", toolsArray);
            return result;
        }

        private JsonObject handleToolsCall(JsonObject params) throws Exception {
            String toolName = params.has("name") ? params.get("name").getAsString() : "";
            JsonObject arguments = params.has("arguments")
                    ? params.getAsJsonObject("arguments")
                    : new JsonObject();

            McpTool tool = findTool(toolName);
            if (tool == null) {
                throw new Exception("Unknown tool: " + toolName);
            }

            String toolResult = tool.execute(arguments);

            JsonObject result = new JsonObject();
            JsonArray content = new JsonArray();
            JsonObject textContent = new JsonObject();
            textContent.addProperty("type", "text");
            textContent.addProperty("text", toolResult);
            content.add(textContent);
            result.add("content", content);
            result.addProperty("isError", false);

            return result;
        }

        private McpTool findTool(String name) {
            for (McpTool tool : tools) {
                if (tool.getName().equals(name)) {
                    return tool;
                }
            }
            return null;
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] response = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    /**
     * Health check endpoint.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"ok\",\"tools\":" + tools.size() + "}";
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}
