package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_run_unit_test -- Run ABAP Unit tests.
 */
public class RunUnitTestTool extends AbstractMcpTool {

    public static final String NAME = "sap_run_unit_test";

    public RunUnitTestTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Run ABAP Unit tests. Returns pass/fail per test class and method.";
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

        String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">"
                + "<external><coverage active=\"false\"/></external>"
                + "<options>"
                + "<uriType value=\"semantic\"/>"
                + "<testDeterminationStrategy sameProgram=\"true\" assignedTests=\"false\"/>"
                + "<testRiskLevels harmless=\"true\" dangerous=\"false\" critical=\"false\"/>"
                + "<testDurations short=\"true\" medium=\"false\" long=\"false\"/>"
                + "<withNavigationUri enabled=\"false\"/>"
                + "</options>"
                + "<adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<objectSet kind=\"inclusive\">"
                + "<adtcore:objectReferences>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</adtcore:objectReferences>"
                + "</objectSet>"
                + "</adtcore:objectSets>"
                + "</aunit:runConfiguration>";

        HttpResponse<String> response = client.post("/sap/bc/adt/abapunit/testruns",
                xmlBody, "application/*", "application/*");

        if (response.statusCode() >= 400) {
            throw new RuntimeException("AUnit run failed (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonObject result = AdtXmlParser.parseUnitTestResults(response.body());
        return result.toString();
    }
}
