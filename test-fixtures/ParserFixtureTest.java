import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtXmlParser;
import com.sap.adt.mcp.tools.SyntaxCheckTool;

/**
 * Dependency-free regression tests for the ADT response parsers, driven by
 * real responses captured from an SAP system (see capture-fixtures.ps1).
 * Files prefixed "synthetic_" are hand-written in the documented ADT format.
 *
 * Run: bash test-fixtures/run-tests.sh
 */
public class ParserFixtureTest {

    private static final List<String> failures = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "test-fixtures");

        // #5a: quick search returns adtcore:objectReference in namespace http://www.sap.com/adt/core
        JsonArray search = AdtXmlParser.parseSearchResults(read(dir, "search_quick.xml"));
        check("search: one hit", search.size() == 1);
        if (search.size() == 1) {
            JsonObject hit = search.get(0).getAsJsonObject();
            check("search: name", "CL_ABAP_TYPEDESCR".equals(hit.get("name").getAsString()));
            check("search: type", "CLAS/OC".equals(hit.get("type").getAsString()));
            check("search: package", "SABP_RTTI".equals(hit.get("packageName").getAsString()));
            check("search: uri", "/sap/bc/adt/oo/classes/cl_abap_typedescr".equals(hit.get("uri").getAsString()));
        }

        // #5b: data preview is column-oriented (columns -> metadata + dataSet/data)
        JsonObject preview = AdtXmlParser.parseDataPreview(read(dir, "datapreview_t000.xml"));
        JsonArray cols = preview.getAsJsonArray("columns");
        JsonArray rows = preview.getAsJsonArray("rows");
        check("sql: 2 columns", cols.size() == 2);
        if (cols.size() == 2) {
            check("sql: column names", "MANDT".equals(cols.get(0).getAsJsonObject().get("name").getAsString())
                    && "MTEXT".equals(cols.get(1).getAsJsonObject().get("name").getAsString()));
        }
        check("sql: rowCount 2", preview.has("rowCount") && preview.get("rowCount").getAsInt() == 2);
        check("sql: row 0", rows.size() > 0 && "[\"000\",\"SAP SE\"]".equals(rows.get(0).toString()));
        check("sql: row 1", rows.size() > 1 && "[\"001\",\"SAP SE\"]".equals(rows.get(1).toString()));

        // Parse failures must surface as errors, not as an empty result
        check("search: malformed XML throws", throwsOn(() -> AdtXmlParser.parseSearchResults("<broken")));
        check("sql: malformed XML throws", throwsOn(() -> AdtXmlParser.parseDataPreview("<broken")));

        // #6: request must use adtcore:uri (object URL) + chkrun:version; old payload gave uriMappingError
        String payload = SyntaxCheckTool.buildCheckRunXml("/sap/bc/adt/oo/classes/cl_abap_typedescr/source/main");
        check("syntax: adtcore:uri with object URL",
                payload.contains("adtcore:uri=\"/sap/bc/adt/oo/classes/cl_abap_typedescr\""));
        check("syntax: chkrun:version", payload.contains("chkrun:version=\"active\""));
        check("syntax: no chkrun:uri", !payload.contains("chkrun:uri="));
        check("syntax: clean report has no messages",
                AdtXmlParser.parseSyntaxCheckResults(read(dir, "checkrun_B_fixed.xml")).size() == 0);

        // #7: activation errors arrive with HTTP 200 in the body
        JsonObject act = AdtXmlParser.parseActivationResult(read(dir, "synthetic_activation_error.xml"));
        check("activation: error => success false", !act.get("success").getAsBoolean());
        check("activation: message text", act.getAsJsonArray("messages").toString().contains("FOO"));

        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("  FAIL: " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static String read(Path dir, String name) throws Exception {
        return Files.readString(dir.resolve(name));
    }

    private static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ok   " + name); }
        else    { failures.add(name); System.out.println("  FAIL " + name); }
    }

    private static boolean throwsOn(Runnable r) {
        try { r.run(); return false; } catch (RuntimeException e) { return true; }
    }
}
