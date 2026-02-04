package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_atc_run -- Run ATC quality checks.
 */
public class AtcRunTool extends AbstractMcpTool {

    public static final String NAME = "sap_atc_run";

    public AtcRunTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Run ATC quality checks. Returns findings with priority and messages.";
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

        String variant = "DEFAULT";
        int maxResults = 100;

        // Create worklist
        String worklistId;
        try {
            HttpResponse<String> wlResponse = client.post(
                    "/sap/bc/adt/atc/worklists?checkVariant=" + urlEncode(variant),
                    "", "application/xml", "text/plain");
            worklistId = wlResponse.body() != null ? wlResponse.body().trim() : variant;
        } catch (Exception e) {
            worklistId = variant;
        }

        if (worklistId.isEmpty()) worklistId = variant;

        // Run ATC
        String runXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<atc:run maximumVerdicts=\"" + maxResults + "\" xmlns:atc=\"http://www.sap.com/adt/atc\">"
                + "<objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<objectSet kind=\"inclusive\">"
                + "<adtcore:objectReferences>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</adtcore:objectReferences>"
                + "</objectSet>"
                + "</objectSets>"
                + "</atc:run>";

        HttpResponse<String> runResponse = client.post(
                "/sap/bc/adt/atc/runs?worklistId=" + urlEncode(worklistId),
                runXml, "application/xml", "application/xml");

        // Extract worklist ID from response
        String runWorklistId = extractWorklistId(runResponse);
        if (runWorklistId != null && !runWorklistId.isEmpty()) {
            worklistId = runWorklistId;
        }

        // Fetch results
        HttpResponse<String> worklistResponse = client.get(
                "/sap/bc/adt/atc/worklists/" + urlEncode(worklistId),
                "application/atc.worklist.v1+xml");

        JsonObject worklist = AdtXmlParser.parseAtcWorklist(worklistResponse.body());
        worklist.addProperty("worklistId", worklistId);
        return worklist.toString();
    }

    private String extractWorklistId(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location").orElse(null);
        if (location != null && !location.isEmpty()) {
            int lastSlash = location.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < location.length() - 1) {
                String id = location.substring(lastSlash + 1);
                int qMark = id.indexOf('?');
                if (qMark >= 0) id = id.substring(0, qMark);
                return id;
            }
        }
        return null;
    }
}
