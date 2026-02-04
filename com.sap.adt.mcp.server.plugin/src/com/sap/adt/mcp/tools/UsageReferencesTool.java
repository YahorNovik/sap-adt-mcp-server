package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_usage_references -- Find where-used list.
 */
public class UsageReferencesTool extends AbstractMcpTool {

    public static final String NAME = "sap_usage_references";

    public UsageReferencesTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Find all usages (where-used) of an ABAP element across the system.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String url = resolveObjectUrlArg(arguments, "url");
        if (url == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/repository/informationsystem/usagereferences");
        path.append("?uri=").append(urlEncode(url));

        HttpResponse<String> response = client.post(path.toString(), "", "application/*", "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
