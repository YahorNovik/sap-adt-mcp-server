package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_search_object -- Search for ABAP repository objects.
 */
public class SearchObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_search_object";

    public SearchObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Search for ABAP objects by name pattern. Returns names, types, and URIs.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search query string (supports wildcards, e.g. 'Z_MY_*')");

        JsonObject objTypeProp = new JsonObject();
        objTypeProp.addProperty("type", "string");
        objTypeProp.addProperty("description",
                "Optional ADT object type filter (e.g. 'PROG/P' for programs, 'CLAS/OC' for classes)");

        JsonObject maxProp = new JsonObject();
        maxProp.addProperty("type", "integer");
        maxProp.addProperty("description", "Maximum number of results to return (default 100)");

        JsonObject properties = new JsonObject();
        properties.add("query", queryProp);
        properties.add("objType", objTypeProp);
        properties.add("max", maxProp);

        JsonArray required = new JsonArray();
        required.add("query");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String query = arguments.get("query").getAsString();
        String objType = optString(arguments, "objType");
        int max = optInt(arguments, "max", 100);

        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/repository/informationsystem/search")
            .append("?operation=quickSearch")
            .append("&query=").append(urlEncode(query))
            .append("&maxResults=").append(max);

        if (objType != null && !objType.isEmpty()) {
            path.append("&objectType=").append(urlEncode(objType));
        }

        HttpResponse<String> response = client.get(path.toString(), "application/*");
        JsonArray results = AdtXmlParser.parseSearchResults(response.body());

        JsonObject output = new JsonObject();
        output.addProperty("totalResults", results.size());
        output.add("results", results);
        return output.toString();
    }
}
