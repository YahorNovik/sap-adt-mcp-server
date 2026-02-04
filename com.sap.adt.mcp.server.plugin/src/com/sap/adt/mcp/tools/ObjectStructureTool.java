package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_object_structure -- Get object metadata and structure.
 */
public class ObjectStructureTool extends AbstractMcpTool {

    public static final String NAME = "sap_object_structure";

    public ObjectStructureTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Get structure/metadata of an ABAP object. Returns links, includes, and source URLs.";
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
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        HttpResponse<String> response = client.get(objectUrl, "application/*");
        JsonObject structure = AdtXmlParser.parseObjectStructure(response.body());
        return structure.toString();
    }
}
