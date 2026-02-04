package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_activate -- Activate ABAP objects.
 */
public class ActivateTool extends AbstractMcpTool {

    public static final String NAME = "sap_activate";

    public ActivateTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Activate an ABAP object after editing. Required to make changes effective.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        String objectName = extractObjectName(objectUrl);

        String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                + "\" adtcore:name=\"" + escapeXml(objectName) + "\"/>"
                + "</adtcore:objectReferences>";

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                activateXml,
                "application/xml",
                "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

        JsonObject result = AdtXmlParser.parseActivationResult(response.body());

        JsonObject output = new JsonObject();
        output.addProperty("success", result.get("success").getAsBoolean());
        output.add("messages", result.get("messages"));

        return output.toString();
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
}
