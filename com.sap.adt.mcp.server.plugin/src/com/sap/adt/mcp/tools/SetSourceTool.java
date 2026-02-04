package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_set_source -- Write ABAP source code.
 */
public class SetSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_set_source";

    public SetSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Write ABAP source code to existing object. Locks, writes, unlocks, and activates automatically.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "The complete ABAP source code to write");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("source", sourceProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (objectSourceUrl == null) {
            throw new IllegalArgumentException("Provide either objectType + objectName, or objectSourceUrl.");
        }
        objectSourceUrl = ensureSourceUrl(objectSourceUrl);
        String source = arguments.get("source").getAsString();
        String transport = optString(arguments, "transport");

        if (isFunctionModuleUrl(objectSourceUrl)) {
            source = sanitizeFmSource(source);
        }

        return lockWriteUnlock(objectSourceUrl, source, transport);
    }

    private String lockWriteUnlock(String objectSourceUrl, String source, String transport) throws Exception {
        final int maxAttempts = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return lockWriteUnlockAttempt(objectSourceUrl, source, transport);
            } catch (java.io.IOException e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("HTTP 423") && attempt < maxAttempts) {
                    Thread.sleep(500);
                    continue;
                }
                throw e;
            }
        }
        throw lastError;
    }

    private String lockWriteUnlockAttempt(String sourceUrl, String source, String transport) throws Exception {
        String lockUrl = toLockUrl(sourceUrl);

        // Lock
        String lockPath = lockUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());

        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new RuntimeException("Failed to acquire lock on " + lockUrl + ". Response: " + lockResp.body());
        }

        try {
            // Write
            String writePath = sourceUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath = writePath + "&corrNr=" + urlEncode(transport);
            }

            HttpResponse<String> response = client.putWithHeaders(writePath, source,
                    "text/plain; charset=utf-8", STATEFUL_HEADERS);

            JsonObject output = new JsonObject();
            output.addProperty("status", "success");
            output.addProperty("statusCode", response.statusCode());

            // Activate
            String objectUrl = sourceUrl;
            if (objectUrl.endsWith("/source/main")) {
                objectUrl = objectUrl.substring(0, objectUrl.length() - "/source/main".length());
            }
            String objectName = extractObjectName(objectUrl);
            try {
                String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                        + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                        + "\" adtcore:name=\"" + escapeXml(objectName) + "\"/>"
                        + "</adtcore:objectReferences>";

                client.post(
                        "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                        activateXml,
                        "application/xml",
                        "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

                output.addProperty("activated", true);
            } catch (Exception e) {
                output.addProperty("activated", false);
                output.addProperty("activationError", e.getMessage());
            }

            return output.toString();
        } finally {
            safeUnlock(lockUrl, lockHandle);
        }
    }

    private String extractObjectName(String objectUrl) {
        if (objectUrl == null) return "UNKNOWN";
        String[] parts = objectUrl.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i].toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    private void safeUnlock(String lockUrl, String lockHandle) {
        try {
            String unlockPath = lockUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
            client.postWithHeaders(unlockPath, "", "application/*", "application/*", STATEFUL_HEADERS);
        } catch (Exception e) {
            // Ignore
        }
    }
}
