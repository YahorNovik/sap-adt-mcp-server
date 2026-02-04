package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_get_source -- Retrieve ABAP source code.
 */
public class GetSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_source";

    public GetSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read source code of an ABAP object. Provide objectType (CLAS/PROG/INTF/etc) + objectName.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description", "Optional version: 'active', 'inactive', or 'workingArea'");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("version", versionProp);

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
        String version = optString(arguments, "version");

        String path = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (path == null) {
            throw new IllegalArgumentException("Provide either objectType + objectName, or objectSourceUrl.");
        }

        if (version != null && !version.isEmpty()) {
            String separator = path.contains("?") ? "&" : "?";
            path = path + separator + "version=" + urlEncode(version);
        }

        HttpResponse<String> response = client.get(path, "text/plain");
        return response.body();
    }
}
