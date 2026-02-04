package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_abap_docu -- Get ABAP documentation for keywords, statements, classes, etc.
 */
public class AbapDocuTool extends AbstractMcpTool {

    public static final String NAME = "sap_abap_docu";

    public AbapDocuTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Get ABAP documentation for keywords, statements, classes, methods, or function modules. "
                + "Use this to look up syntax, parameters, and usage examples.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject searchTermProp = new JsonObject();
        searchTermProp.addProperty("type", "string");
        searchTermProp.addProperty("description",
                "The ABAP element to look up (e.g. 'SELECT', 'LOOP', 'CL_SALV_TABLE', 'STRING_AGG')");

        JsonObject docuTypeProp = new JsonObject();
        docuTypeProp.addProperty("type", "string");
        docuTypeProp.addProperty("description",
                "Documentation type: 'keyword' (ABAP statements), 'class' (global class), "
                + "'function' (function module), 'type' (data type). Default: auto-detect");

        JsonObject properties = new JsonObject();
        properties.add("searchTerm", searchTermProp);
        properties.add("docuType", docuTypeProp);

        JsonArray required = new JsonArray();
        required.add("searchTerm");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String searchTerm = arguments.get("searchTerm").getAsString().toUpperCase().trim();
        String docuType = optString(arguments, "docuType");

        JsonObject result = new JsonObject();
        result.addProperty("searchTerm", searchTerm);

        // Try to auto-detect documentation type if not specified
        if (docuType == null || docuType.isEmpty()) {
            docuType = detectDocuType(searchTerm);
        }
        result.addProperty("docuType", docuType);

        String documentation = null;

        switch (docuType.toLowerCase()) {
            case "keyword":
                documentation = fetchKeywordDocu(searchTerm);
                break;
            case "class":
                documentation = fetchClassDocu(searchTerm);
                break;
            case "function":
                documentation = fetchFunctionDocu(searchTerm);
                break;
            case "type":
                documentation = fetchTypeDocu(searchTerm);
                break;
            default:
                // Try keyword first, then class
                documentation = fetchKeywordDocu(searchTerm);
                if (documentation == null || documentation.isEmpty()) {
                    documentation = fetchClassDocu(searchTerm);
                }
        }

        if (documentation != null && !documentation.isEmpty()) {
            result.addProperty("found", true);
            result.addProperty("documentation", documentation);
        } else {
            result.addProperty("found", false);
            result.addProperty("message", "No documentation found for '" + searchTerm + "'");
        }

        return result.toString();
    }

    private String detectDocuType(String term) {
        // Classes typically start with CL_, ZCL_, /namespace/CL_
        if (term.startsWith("CL_") || term.startsWith("ZCL_") || term.startsWith("YCL_")
                || term.contains("/CL_") || term.startsWith("IF_") || term.startsWith("ZIF_")) {
            return "class";
        }
        // Function modules often contain underscores and don't match keyword patterns
        if (term.contains("_") && !isKnownKeyword(term)) {
            return "function";
        }
        // Default to keyword for ABAP statements
        return "keyword";
    }

    private boolean isKnownKeyword(String term) {
        // Common ABAP keywords
        String[] keywords = {
            "SELECT", "INSERT", "UPDATE", "DELETE", "MODIFY",
            "LOOP", "ENDLOOP", "IF", "ENDIF", "CASE", "ENDCASE",
            "DO", "ENDDO", "WHILE", "ENDWHILE",
            "READ", "APPEND", "COLLECT", "SORT", "CLEAR",
            "DATA", "TYPES", "CONSTANTS", "FIELD-SYMBOLS",
            "CLASS", "INTERFACE", "METHOD", "ENDMETHOD",
            "TRY", "CATCH", "ENDTRY", "RAISE", "CLEANUP",
            "CALL", "PERFORM", "FORM", "ENDFORM",
            "WRITE", "MESSAGE", "ASSERT",
            "OPEN", "CLOSE", "TRANSFER", "RECEIVE",
            "COMMIT", "ROLLBACK", "SET", "GET",
            "AUTHORITY-CHECK", "ASSIGN", "UNASSIGN",
            "CONCATENATE", "SPLIT", "REPLACE", "TRANSLATE", "CONDENSE",
            "MOVE", "MOVE-CORRESPONDING", "CORRESPONDING",
            "NEW", "VALUE", "REF", "CAST", "CONV", "COND", "SWITCH",
            "FOR", "REDUCE", "FILTER", "GROUP BY",
            "INTO", "FROM", "WHERE", "ORDER BY", "UP TO",
            "STRING_AGG", "COALESCE", "CAST"
        };
        for (String kw : keywords) {
            if (term.equalsIgnoreCase(kw)) return true;
        }
        return false;
    }

    private String fetchKeywordDocu(String keyword) {
        try {
            // ABAP keyword documentation endpoint
            String path = "/sap/bc/adt/docu/abap/langu?object=" + urlEncode(keyword) + "&language=EN";
            HttpResponse<String> response = client.get(path, "text/html, text/plain, application/xml");

            if (response.statusCode() == 200 && response.body() != null) {
                return AdtXmlParser.parseAbapDocu(response.body());
            }
        } catch (Exception e) {
            System.err.println("fetchKeywordDocu failed: " + e.getMessage());
        }
        return null;
    }

    private String fetchClassDocu(String className) {
        try {
            // Try to get class documentation via ADT
            String path = "/sap/bc/adt/docu/abap/langu?object=" + urlEncode(className)
                    + "&type=CLAS&language=EN";
            HttpResponse<String> response = client.get(path, "text/html, text/plain, application/xml");

            if (response.statusCode() == 200 && response.body() != null) {
                String docu = AdtXmlParser.parseAbapDocu(response.body());
                if (docu != null && !docu.isEmpty()) {
                    return docu;
                }
            }

            // Fallback: get class metadata
            path = "/sap/bc/adt/oo/classes/" + urlEncode(className.toLowerCase());
            response = client.get(path, "application/vnd.sap.adt.oo.classes.v4+xml, application/xml");

            if (response.statusCode() == 200 && response.body() != null) {
                JsonObject structure = AdtXmlParser.parseObjectStructure(response.body());
                return formatClassStructure(className, structure);
            }
        } catch (Exception e) {
            System.err.println("fetchClassDocu failed: " + e.getMessage());
        }
        return null;
    }

    private String fetchFunctionDocu(String funcName) {
        try {
            String path = "/sap/bc/adt/docu/abap/langu?object=" + urlEncode(funcName)
                    + "&type=FUNC&language=EN";
            HttpResponse<String> response = client.get(path, "text/html, text/plain, application/xml");

            if (response.statusCode() == 200 && response.body() != null) {
                return AdtXmlParser.parseAbapDocu(response.body());
            }
        } catch (Exception e) {
            System.err.println("fetchFunctionDocu failed: " + e.getMessage());
        }
        return null;
    }

    private String fetchTypeDocu(String typeName) {
        try {
            String path = "/sap/bc/adt/docu/abap/langu?object=" + urlEncode(typeName)
                    + "&type=TYPE&language=EN";
            HttpResponse<String> response = client.get(path, "text/html, text/plain, application/xml");

            if (response.statusCode() == 200 && response.body() != null) {
                return AdtXmlParser.parseAbapDocu(response.body());
            }
        } catch (Exception e) {
            System.err.println("fetchTypeDocu failed: " + e.getMessage());
        }
        return null;
    }

    private String formatClassStructure(String className, JsonObject structure) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(className).append("\n");

        if (structure.has("description") && !structure.get("description").getAsString().isEmpty()) {
            sb.append("Description: ").append(structure.get("description").getAsString()).append("\n");
        }
        if (structure.has("packageName")) {
            sb.append("Package: ").append(structure.get("packageName").getAsString()).append("\n");
        }

        if (structure.has("includes")) {
            sb.append("\nIncludes:\n");
            for (var inc : structure.getAsJsonArray("includes")) {
                JsonObject incObj = inc.getAsJsonObject();
                sb.append("  - ").append(incObj.get("includeType").getAsString())
                  .append(": ").append(incObj.get("name").getAsString()).append("\n");
            }
        }

        return sb.toString();
    }
}
