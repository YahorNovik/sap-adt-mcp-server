package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_create_object -- Create new ABAP objects.
 */
public class CreateObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_object";

    private static final Map<String, String> TYPE_URL_MAP = new HashMap<>();
    private static final Map<String, String> TYPE_CONTENT_TYPE_MAP = new HashMap<>();

    static {
        TYPE_URL_MAP.put("PROG/P", "/sap/bc/adt/programs/programs");
        TYPE_URL_MAP.put("CLAS/OC", "/sap/bc/adt/oo/classes");
        TYPE_URL_MAP.put("INTF/OI", "/sap/bc/adt/oo/interfaces");
        TYPE_URL_MAP.put("FUGR/F", "/sap/bc/adt/functions/groups");

        TYPE_CONTENT_TYPE_MAP.put("PROG/P", "application/vnd.sap.adt.programs.programs.v2+xml");
        TYPE_CONTENT_TYPE_MAP.put("CLAS/OC", "application/vnd.sap.adt.oo.classes.v4+xml");
        TYPE_CONTENT_TYPE_MAP.put("INTF/OI", "application/vnd.sap.adt.oo.interfaces.v5+xml");
        TYPE_CONTENT_TYPE_MAP.put("FUGR/F", "application/vnd.sap.adt.functions.groups.v3+xml");
    }

    public CreateObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Create a new ABAP object (program, class, interface, function group).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject objtypeProp = new JsonObject();
        objtypeProp.addProperty("type", "string");
        objtypeProp.addProperty("description",
                "ADT object type: 'PROG/P' (program), 'CLAS/OC' (class), 'INTF/OI' (interface), 'FUGR/F' (function group)");

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Object name (e.g. 'ZTEST_PROGRAM')");

        JsonObject parentNameProp = new JsonObject();
        parentNameProp.addProperty("type", "string");
        parentNameProp.addProperty("description", "Parent package name (e.g. '$TMP')");

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description", "Short description");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number");

        JsonObject properties = new JsonObject();
        properties.add("objtype", objtypeProp);
        properties.add("name", nameProp);
        properties.add("parentName", parentNameProp);
        properties.add("description", descProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objtype");
        required.add("name");
        required.add("parentName");
        required.add("description");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objtype = arguments.get("objtype").getAsString().toUpperCase();
        String name = arguments.get("name").getAsString();
        String parentName = arguments.get("parentName").getAsString();
        String description = arguments.get("description").getAsString();
        String transport = optString(arguments, "transport");

        if (description.length() > 60) description = description.substring(0, 60);

        String creationUrl = TYPE_URL_MAP.get(objtype);
        if (creationUrl == null) {
            throw new IllegalArgumentException("Unsupported type: " + objtype + ". Supported: " + TYPE_URL_MAP.keySet());
        }

        String contentType = TYPE_CONTENT_TYPE_MAP.getOrDefault(objtype, "application/xml");
        String xmlBody = buildCreationXml(objtype, name, parentName, description);

        String path = creationUrl;
        if (transport != null && !transport.isEmpty()) {
            path = path + "?corrNr=" + urlEncode(transport);
        }

        HttpResponse<String> response = client.post(path, xmlBody, contentType, contentType + ", application/xml");

        JsonObject output = new JsonObject();
        output.addProperty("status", "created");
        output.addProperty("name", name);
        output.addProperty("type", objtype);
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("objectUrl", creationUrl + "/" + name.toLowerCase());

        return output.toString();
    }

    private String buildCreationXml(String objtype, String name, String parentName, String description) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        if (objtype.startsWith("PROG")) {
            xml.append("<program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"PROG/P\" adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</program:abapProgram>");
        } else if (objtype.startsWith("CLAS")) {
            xml.append("<class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"CLAS/OC\" adtcore:masterLanguage=\"EN\" ");
            xml.append("class:final=\"true\" class:visibility=\"public\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</class:abapClass>");
        } else if (objtype.startsWith("INTF")) {
            xml.append("<intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"INTF/OI\" adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</intf:abapInterface>");
        } else if (objtype.equals("FUGR/F")) {
            xml.append("<group:abapFunctionGroup xmlns:group=\"http://www.sap.com/adt/functions/groups\" ");
            xml.append("xmlns:adtcore=\"http://www.sap.com/adt/core\" ");
            xml.append("adtcore:description=\"").append(escapeXml(description)).append("\" ");
            xml.append("adtcore:language=\"EN\" ");
            xml.append("adtcore:name=\"").append(escapeXml(name)).append("\" ");
            xml.append("adtcore:type=\"FUGR/F\" adtcore:masterLanguage=\"EN\">");
            xml.append("<adtcore:packageRef adtcore:name=\"").append(escapeXml(parentName)).append("\"/>");
            xml.append("</group:abapFunctionGroup>");
        }

        return xml.toString();
    }
}
