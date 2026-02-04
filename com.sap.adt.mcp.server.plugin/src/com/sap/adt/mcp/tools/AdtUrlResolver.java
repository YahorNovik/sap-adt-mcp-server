package com.sap.adt.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Utility for resolving ABAP object types and names to ADT REST URLs.
 */
public final class AdtUrlResolver {

    private AdtUrlResolver() {}

    private static final Map<String, String> OBJECT_URL_TEMPLATES = new LinkedHashMap<>();
    static {
        OBJECT_URL_TEMPLATES.put("CLAS", "/sap/bc/adt/oo/classes/{name}");
        OBJECT_URL_TEMPLATES.put("CLASS", "/sap/bc/adt/oo/classes/{name}");
        OBJECT_URL_TEMPLATES.put("INTF", "/sap/bc/adt/oo/interfaces/{name}");
        OBJECT_URL_TEMPLATES.put("INTERFACE", "/sap/bc/adt/oo/interfaces/{name}");
        OBJECT_URL_TEMPLATES.put("PROG", "/sap/bc/adt/programs/programs/{name}");
        OBJECT_URL_TEMPLATES.put("PROGRAM", "/sap/bc/adt/programs/programs/{name}");
        OBJECT_URL_TEMPLATES.put("TABL", "/sap/bc/adt/ddic/tables/{name}");
        OBJECT_URL_TEMPLATES.put("TABLE", "/sap/bc/adt/ddic/tables/{name}");
        OBJECT_URL_TEMPLATES.put("STRU", "/sap/bc/adt/ddic/structures/{name}");
        OBJECT_URL_TEMPLATES.put("STRUCTURE", "/sap/bc/adt/ddic/structures/{name}");
        OBJECT_URL_TEMPLATES.put("DDLS", "/sap/bc/adt/ddic/ddl/sources/{name}");
        OBJECT_URL_TEMPLATES.put("CDS", "/sap/bc/adt/ddic/ddl/sources/{name}");
        OBJECT_URL_TEMPLATES.put("DTEL", "/sap/bc/adt/ddic/dataelements/{name}");
        OBJECT_URL_TEMPLATES.put("DATAELEMENT", "/sap/bc/adt/ddic/dataelements/{name}");
        OBJECT_URL_TEMPLATES.put("DOMA", "/sap/bc/adt/ddic/domains/{name}");
        OBJECT_URL_TEMPLATES.put("DOMAIN", "/sap/bc/adt/ddic/domains/{name}");
        OBJECT_URL_TEMPLATES.put("SRVD", "/sap/bc/adt/ddic/srvd/sources/{name}");
        OBJECT_URL_TEMPLATES.put("DDLX", "/sap/bc/adt/ddic/ddlx/sources/{name}");
        OBJECT_URL_TEMPLATES.put("BDEF", "/sap/bc/adt/bopf/bdef/sources/{name}");
    }

    public static String resolveObjectUrl(String objectType, String objectName) {
        if (objectType == null || objectType.isEmpty()
                || objectName == null || objectName.isEmpty()) {
            return null;
        }
        String template = OBJECT_URL_TEMPLATES.get(objectType.toUpperCase());
        if (template != null) {
            return template.replace("{name}", objectName.toLowerCase());
        }
        return null;
    }

    public static String resolveSourceUrl(String objectType, String objectName) {
        String objectUrl = resolveObjectUrl(objectType, objectName);
        if (objectUrl != null) {
            return objectUrl + "/source/main";
        }
        return null;
    }

    public static JsonArray buildTypeEnumArray() {
        JsonArray arr = new JsonArray();
        arr.add("CLAS"); arr.add("INTF"); arr.add("PROG");
        arr.add("TABL"); arr.add("STRU"); arr.add("DDLS");
        arr.add("CDS");  arr.add("DTEL"); arr.add("DOMA");
        arr.add("SRVD"); arr.add("DDLX"); arr.add("BDEF");
        return arr;
    }

    public static final String TYPE_DESCRIPTION =
            "Object type: CLAS (class), INTF (interface), PROG (program), "
            + "TABL (table), STRU (structure), DDLS/CDS (CDS view), "
            + "DTEL (data element), DOMA (domain), SRVD (service definition), "
            + "DDLX (metadata extension), BDEF (behavior definition)";

    public static JsonObject buildTypeProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", TYPE_DESCRIPTION);
        prop.add("enum", buildTypeEnumArray());
        return prop;
    }

    public static JsonObject buildNameProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", "Object name (e.g. 'ZCL_MY_CLASS', 'MARA'). Case-insensitive.");
        return prop;
    }
}
