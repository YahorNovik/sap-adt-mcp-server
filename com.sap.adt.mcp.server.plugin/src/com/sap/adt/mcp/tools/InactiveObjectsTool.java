package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_inactive_objects -- List inactive objects for a user.
 */
public class InactiveObjectsTool extends AbstractMcpTool {

    public static final String NAME = "sap_inactive_objects";

    public InactiveObjectsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List all inactive (not yet activated) ABAP objects for the current user.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        HttpResponse<String> response = client.get(
                "/sap/bc/adt/activation/inactiveobjects",
                "application/vnd.sap.adt.inactivectsobjects.v1+xml");

        JsonObject result = AdtXmlParser.parseInactiveObjects(response.body());
        return result.toString();
    }
}
