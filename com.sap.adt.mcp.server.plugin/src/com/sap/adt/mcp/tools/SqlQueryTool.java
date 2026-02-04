package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_sql_query -- Execute ABAP SQL query.
 */
public class SqlQueryTool extends AbstractMcpTool {

    public static final String NAME = "sap_sql_query";

    public SqlQueryTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Execute an ABAP SQL SELECT query against SAP database tables and return DATA ROWS.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "The ABAP SQL query (e.g. 'SELECT * FROM mara UP TO 10 ROWS')");

        JsonObject maxRowsProp = new JsonObject();
        maxRowsProp.addProperty("type", "integer");
        maxRowsProp.addProperty("description", "Maximum rows to return (default: 100)");

        JsonObject properties = new JsonObject();
        properties.add("query", queryProp);
        properties.add("maxRows", maxRowsProp);

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
        int maxRows = optInt(arguments, "maxRows", 100);

        String path = "/sap/bc/adt/datapreview/freestyle?rowNumber=" + maxRows;
        HttpResponse<String> resp = client.post(path, query,
                "text/plain; charset=utf-8",
                "application/vnd.sap.adt.datapreview.table.v1+xml");

        JsonObject result = AdtXmlParser.parseDataPreview(resp.body());
        return result.toString();
    }
}
