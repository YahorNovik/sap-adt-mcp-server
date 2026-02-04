package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_unlock -- Unlock an ABAP object.
 */
public class UnlockTool extends AbstractMcpTool {

    public static final String NAME = "sap_unlock";

    public UnlockTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Unlock an ABAP object using a lock handle.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject lockHandleProp = new JsonObject();
        lockHandleProp.addProperty("type", "string");
        lockHandleProp.addProperty("description", "The lock handle from sap_lock");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("lockHandle", lockHandleProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("lockHandle");

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
        String lockHandle = arguments.get("lockHandle").getAsString();

        String unlockPath = objectUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
        HttpResponse<String> resp = client.postWithHeaders(unlockPath, "",
                "application/*", "application/*", STATEFUL_HEADERS);

        JsonObject output = new JsonObject();
        output.addProperty("success", true);
        output.addProperty("statusCode", resp.statusCode());

        return output.toString();
    }
}
