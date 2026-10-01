package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_syntax_check -- Run syntax check on ABAP source code.
 */
public class SyntaxCheckTool extends AbstractMcpTool {

    public static final String NAME = "sap_syntax_check";

    public SyntaxCheckTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Builds the ADT checkrun request body. The backend expects the OBJECT URL in
     * adtcore:uri plus chkrun:version; sending the source URL in chkrun:uri fails
     * with "uriMappingError: No URI-Mapping defined for URI" (HTTP 500).
     */
    public static String buildCheckRunXml(String sourceUrl) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                + "  <chkrun:checkObject adtcore:uri=\"" + escapeXml(toObjectUrl(sourceUrl))
                + "\" chkrun:version=\"active\"/>\n"
                + "</chkrun:checkObjectList>";
    }

    @Override
    public String getDescription() {
        return "Run ABAP syntax check on source code. Returns errors and warnings with line numbers.";
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
        String sourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (sourceUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }
        sourceUrl = ensureSourceUrl(sourceUrl);

        String checkXml = buildCheckRunXml(sourceUrl);

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/checkruns?reporters=abapCheckRun",
                checkXml,
                "application/vnd.sap.adt.checkobjects+xml",
                "application/vnd.sap.adt.checkmessages+xml");

        JsonArray results = AdtXmlParser.parseSyntaxCheckResults(response.body());

        int errorCount = 0;
        int warningCount = 0;
        for (int i = 0; i < results.size(); i++) {
            JsonObject item = results.get(i).getAsJsonObject();
            String severity = item.has("severity") ? item.get("severity").getAsString() : "";
            if ("error".equalsIgnoreCase(severity)) {
                errorCount++;
            } else if ("warning".equalsIgnoreCase(severity)) {
                warningCount++;
            }
        }

        JsonObject output = new JsonObject();
        output.addProperty("errors", errorCount);
        output.addProperty("warnings", warningCount);
        output.addProperty("success", errorCount == 0);
        output.add("messages", results);

        return output.toString();
    }
}
