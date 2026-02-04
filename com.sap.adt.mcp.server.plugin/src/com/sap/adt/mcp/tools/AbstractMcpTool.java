package com.sap.adt.mcp.tools;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Base class for all SAP ADT MCP tools.
 */
public abstract class AbstractMcpTool implements McpTool {

    protected final AdtRestClient client;

    protected AbstractMcpTool(AdtRestClient client) {
        this.client = client;
    }

    protected static final Map<String, String> STATEFUL_HEADERS =
            Map.of(AdtRestClient.SESSION_TYPE_HEADER, "stateful");

    protected String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    protected int optInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected String resolveSourceUrlArg(JsonObject arguments, String urlParamName) {
        String type = optString(arguments, "objectType");
        String name = optString(arguments, "objectName");
        String resolved = AdtUrlResolver.resolveSourceUrl(type, name);
        if (resolved != null) {
            return resolved;
        }
        return optString(arguments, urlParamName);
    }

    protected String resolveObjectUrlArg(JsonObject arguments, String urlParamName) {
        String type = optString(arguments, "objectType");
        String name = optString(arguments, "objectName");
        String resolved = AdtUrlResolver.resolveObjectUrl(type, name);
        if (resolved != null) {
            return resolved;
        }
        return optString(arguments, urlParamName);
    }

    protected String urlEncode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String sanitizeFmSource(String source) {
        if (source == null) return null;
        return source.lines()
                .filter(line -> !line.stripLeading().startsWith("*\""))
                .collect(Collectors.joining("\n"));
    }

    public static boolean isFunctionModuleUrl(String url) {
        return url != null && url.toLowerCase().contains("/fmodules/");
    }

    public static String ensureSourceUrl(String url) {
        if (url == null || url.isEmpty()) return url;

        String path = url;
        String query = "";
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) {
            query = path.substring(qIdx);
            path = path.substring(0, qIdx);
        }
        if (path.contains("/source/")) {
            return url;
        }
        if (path.matches(".*/programs/programs/[^/]+")
                || path.matches(".*/oo/classes/[^/]+")
                || path.matches(".*/oo/interfaces/[^/]+")
                || path.matches(".*/fmodules/[^/]+")) {
            return path + "/source/main" + query;
        }
        return url;
    }

    public static String toLockUrl(String sourceUrl) {
        return toObjectUrl(sourceUrl);
    }

    public static String toObjectUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) return sourceUrl;

        String path = sourceUrl;
        String query = "";
        int qIdx = path.indexOf('?');
        if (qIdx >= 0) {
            query = path.substring(qIdx);
            path = path.substring(0, qIdx);
        }
        int sourceIdx = path.indexOf("/source/");
        if (sourceIdx >= 0) {
            return path.substring(0, sourceIdx) + query;
        }
        return sourceUrl;
    }

    protected String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
