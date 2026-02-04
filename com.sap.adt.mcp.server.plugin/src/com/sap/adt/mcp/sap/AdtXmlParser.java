package com.sap.adt.mcp.sap;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Static utility class for parsing XML responses from SAP ADT REST APIs.
 */
public final class AdtXmlParser {

    private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
    private static final String NS_ADT = "http://www.sap.com/adt/api";
    private static final String NS_ADT_CORE = "http://www.sap.com/adt/core";
    private static final String NS_CHKRUN = "http://www.sap.com/adt/checkrun";

    private AdtXmlParser() {}

    public static JsonArray parseSearchResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList refs = doc.getElementsByTagNameNS(NS_ADT, "objectReference");
            if (refs.getLength() == 0) {
                refs = doc.getElementsByTagName("objectReference");
            }

            if (refs.getLength() > 0) {
                for (int i = 0; i < refs.getLength(); i++) {
                    Element ref = (Element) refs.item(i);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", attr(ref, "adtcore:name", attr(ref, "name", "")));
                    entry.addProperty("type", attr(ref, "adtcore:type", attr(ref, "type", "")));
                    entry.addProperty("uri", attr(ref, "uri", ""));
                    entry.addProperty("description", attr(ref, "adtcore:description", attr(ref, "description", "")));
                    entry.addProperty("packageName", attr(ref, "adtcore:packageName", attr(ref, "packageName", "")));
                    results.add(entry);
                }
                return results;
            }

            NodeList entries = doc.getElementsByTagNameNS(NS_ATOM, "entry");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", childText(entry, "title", ""));
                obj.addProperty("uri", childAttr(entry, "link", "href", ""));
                obj.addProperty("type", childText(entry, "category", ""));
                obj.addProperty("description", childText(entry, "summary", ""));
                obj.addProperty("packageName", "");
                results.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSearchResults failed: " + e.getMessage());
        }

        return results;
    }

    public static String extractLockHandle(String xml) {
        if (isBlank(xml)) return "";

        try {
            Document doc = parseDocument(xml);
            NodeList nodes = doc.getElementsByTagName("LOCK_HANDLE");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) return value.trim();
            }
            nodes = doc.getElementsByTagName("lock_handle");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) return value.trim();
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.extractLockHandle failed: " + e.getMessage());
        }
        return "";
    }

    public static JsonArray parseSyntaxCheckResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList messages = doc.getElementsByTagNameNS(NS_CHKRUN, "checkMessage");
            if (messages.getLength() == 0) {
                messages = doc.getElementsByTagName("chkrun:checkMessage");
            }

            if (messages.getLength() > 0) {
                for (int i = 0; i < messages.getLength(); i++) {
                    Element msg = (Element) messages.item(i);
                    JsonObject finding = new JsonObject();

                    String uri = attr(msg, "chkrun:uri", attr(msg, "uri", ""));
                    finding.addProperty("uri", uri);

                    String line = "";
                    String offset = "";
                    int hashIdx = uri.indexOf("#start=");
                    if (hashIdx >= 0) {
                        String fragment = uri.substring(hashIdx + 7);
                        String[] parts = fragment.split(",");
                        if (parts.length >= 1) line = parts[0];
                        if (parts.length >= 2) offset = parts[1];
                    }
                    finding.addProperty("line", line);
                    finding.addProperty("offset", offset);

                    String type = attr(msg, "chkrun:type", attr(msg, "type", ""));
                    String severity;
                    switch (type.toUpperCase()) {
                        case "E": severity = "error"; break;
                        case "W": severity = "warning"; break;
                        case "I": severity = "info"; break;
                        default: severity = type;
                    }
                    finding.addProperty("severity", severity);
                    finding.addProperty("text", attr(msg, "chkrun:shortText", attr(msg, "shortText", "")));
                    results.add(finding);
                }
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSyntaxCheckResults failed: " + e.getMessage());
        }

        return results;
    }

    public static JsonObject parseActivationResult(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        JsonArray messages = new JsonArray();
        result.add("messages", messages);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();
            String severity = attr(root, "severity", attr(root, "chkrun:severity", ""));
            boolean success = true;

            NodeList msgNodes = doc.getElementsByTagName("msg");
            if (msgNodes == null || msgNodes.getLength() == 0) {
                msgNodes = doc.getElementsByTagName("message");
            }

            for (int i = 0; i < msgNodes.getLength(); i++) {
                Element msgEl = (Element) msgNodes.item(i);
                String text = msgEl.getTextContent();
                if (text == null || text.trim().isEmpty()) {
                    text = attr(msgEl, "text", attr(msgEl, "shortText", ""));
                }

                String msgSeverity = attr(msgEl, "severity", attr(msgEl, "type", "")).toLowerCase();

                if (text != null && !text.trim().isEmpty()) {
                    messages.add(text.trim());
                }

                if (msgSeverity.contains("error") || msgSeverity.equals("e")) {
                    success = false;
                }
            }

            if (severity.equalsIgnoreCase("error") || severity.equalsIgnoreCase("E")) {
                success = false;
            }

            if (messages.size() == 0 && !severity.equalsIgnoreCase("error")) {
                success = true;
            }

            result.addProperty("success", success);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseActivationResult failed: " + e.getMessage());
            result.addProperty("success", false);
            messages.add("Parse error: " + e.getMessage());
        }

        return result;
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource source = new InputSource(new StringReader(xml));
        return builder.parse(source);
    }

    private static String attr(Element el, String attrName, String defaultValue) {
        if (el == null) return defaultValue;
        String value = el.getAttribute(attrName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private static String childText(Element parent, String tagName, String defaultValue) {
        if (parent == null) return defaultValue;
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : defaultValue;
        }
        return defaultValue;
    }

    private static String childAttr(Element parent, String tagName, String attrName, String defaultValue) {
        if (parent == null) return defaultValue;
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            Element child = (Element) children.item(0);
            return attr(child, attrName, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Parse unit test results XML.
     */
    public static JsonObject parseUnitTestResults(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        JsonArray alerts = new JsonArray();
        result.add("alerts", alerts);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Look for alert nodes
            NodeList alertNodes = doc.getElementsByTagName("alert");
            for (int i = 0; i < alertNodes.getLength(); i++) {
                Element alert = (Element) alertNodes.item(i);
                JsonObject alertObj = new JsonObject();
                alertObj.addProperty("kind", attr(alert, "kind", ""));
                alertObj.addProperty("severity", attr(alert, "severity", ""));

                // Get title and details
                NodeList titles = alert.getElementsByTagName("title");
                if (titles.getLength() > 0) {
                    alertObj.addProperty("title", titles.item(0).getTextContent());
                }

                NodeList details = alert.getElementsByTagName("detail");
                if (details.getLength() > 0) {
                    alertObj.addProperty("detail", details.item(0).getTextContent());
                }

                String severity = attr(alert, "severity", "").toLowerCase();
                if (severity.contains("fatal") || severity.contains("critical")) {
                    result.addProperty("success", false);
                }

                alerts.add(alertObj);
            }

            // Look for program nodes with test results
            NodeList programs = doc.getElementsByTagName("program");
            JsonArray programResults = new JsonArray();
            for (int i = 0; i < programs.getLength(); i++) {
                Element prog = (Element) programs.item(i);
                JsonObject progObj = new JsonObject();
                progObj.addProperty("name", attr(prog, "adtcore:name", attr(prog, "name", "")));
                progObj.addProperty("uri", attr(prog, "adtcore:uri", attr(prog, "uri", "")));

                // Count test classes and methods
                NodeList testClasses = prog.getElementsByTagName("testClass");
                JsonArray classes = new JsonArray();
                for (int j = 0; j < testClasses.getLength(); j++) {
                    Element tc = (Element) testClasses.item(j);
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("name", attr(tc, "adtcore:name", attr(tc, "name", "")));

                    NodeList methods = tc.getElementsByTagName("testMethod");
                    JsonArray methodArr = new JsonArray();
                    for (int k = 0; k < methods.getLength(); k++) {
                        Element m = (Element) methods.item(k);
                        JsonObject mObj = new JsonObject();
                        mObj.addProperty("name", attr(m, "adtcore:name", attr(m, "name", "")));
                        mObj.addProperty("executionTime", attr(m, "executionTime", "0"));

                        // Check for alerts in method
                        NodeList mAlerts = m.getElementsByTagName("alert");
                        if (mAlerts.getLength() > 0) {
                            Element mAlert = (Element) mAlerts.item(0);
                            String kind = attr(mAlert, "kind", "");
                            mObj.addProperty("status", kind.isEmpty() ? "passed" : kind);
                            if (kind.equalsIgnoreCase("failedAssertion") || kind.equalsIgnoreCase("error")) {
                                result.addProperty("success", false);
                            }
                        } else {
                            mObj.addProperty("status", "passed");
                        }
                        methodArr.add(mObj);
                    }
                    tcObj.add("methods", methodArr);
                    classes.add(tcObj);
                }
                progObj.add("testClasses", classes);
                programResults.add(progObj);
            }
            result.add("programs", programResults);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseUnitTestResults failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse data preview (SQL query) results XML.
     */
    public static JsonObject parseDataPreview(String xml) {
        JsonObject result = new JsonObject();
        JsonArray columns = new JsonArray();
        JsonArray rows = new JsonArray();
        result.add("columns", columns);
        result.add("rows", rows);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Parse column metadata
            NodeList metadataNodes = doc.getElementsByTagName("dataPreview:column");
            if (metadataNodes.getLength() == 0) {
                metadataNodes = doc.getElementsByTagName("column");
            }

            for (int i = 0; i < metadataNodes.getLength(); i++) {
                Element col = (Element) metadataNodes.item(i);
                JsonObject colObj = new JsonObject();
                colObj.addProperty("name", attr(col, "dataPreview:name", attr(col, "name", "COL" + i)));
                colObj.addProperty("type", attr(col, "dataPreview:type", attr(col, "type", "")));
                colObj.addProperty("description", attr(col, "dataPreview:description", attr(col, "description", "")));
                columns.add(colObj);
            }

            // Parse data rows
            NodeList dataRows = doc.getElementsByTagName("dataPreview:row");
            if (dataRows.getLength() == 0) {
                dataRows = doc.getElementsByTagName("row");
            }

            for (int i = 0; i < dataRows.getLength(); i++) {
                Element row = (Element) dataRows.item(i);
                JsonArray rowData = new JsonArray();

                NodeList values = row.getElementsByTagName("dataPreview:value");
                if (values.getLength() == 0) {
                    values = row.getElementsByTagName("value");
                }

                for (int j = 0; j < values.getLength(); j++) {
                    String val = values.item(j).getTextContent();
                    rowData.add(val != null ? val : "");
                }
                rows.add(rowData);
            }

            result.addProperty("rowCount", rows.size());

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseDataPreview failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse ATC worklist results XML.
     */
    public static JsonObject parseAtcWorklist(String xml) {
        JsonObject result = new JsonObject();
        JsonArray findings = new JsonArray();
        result.add("findings", findings);
        result.addProperty("totalFindings", 0);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Parse findings
            NodeList findingNodes = doc.getElementsByTagName("atcfinding");
            if (findingNodes.getLength() == 0) {
                findingNodes = doc.getElementsByTagName("finding");
            }

            for (int i = 0; i < findingNodes.getLength(); i++) {
                Element f = (Element) findingNodes.item(i);
                JsonObject finding = new JsonObject();
                finding.addProperty("checkId", attr(f, "checkId", ""));
                finding.addProperty("checkTitle", attr(f, "checkTitle", ""));
                finding.addProperty("messageId", attr(f, "messageId", ""));
                finding.addProperty("messageTitle", attr(f, "messageTitle", attr(f, "shortText", "")));
                finding.addProperty("priority", attr(f, "priority", ""));
                finding.addProperty("uri", attr(f, "uri", attr(f, "location", "")));

                // Extract line number from URI if present
                String uri = attr(f, "uri", attr(f, "location", ""));
                String line = "";
                int hashIdx = uri.indexOf("#start=");
                if (hashIdx >= 0) {
                    String fragment = uri.substring(hashIdx + 7);
                    String[] parts = fragment.split(",");
                    if (parts.length >= 1) line = parts[0];
                }
                finding.addProperty("line", line);

                findings.add(finding);
            }

            result.addProperty("totalFindings", findings.size());

            // Also check for object-level info
            NodeList objects = doc.getElementsByTagName("atcobject");
            if (objects.getLength() == 0) {
                objects = doc.getElementsByTagName("object");
            }
            JsonArray objectsArr = new JsonArray();
            for (int i = 0; i < objects.getLength(); i++) {
                Element obj = (Element) objects.item(i);
                JsonObject objInfo = new JsonObject();
                objInfo.addProperty("name", attr(obj, "adtcore:name", attr(obj, "name", "")));
                objInfo.addProperty("type", attr(obj, "adtcore:type", attr(obj, "type", "")));
                objInfo.addProperty("uri", attr(obj, "adtcore:uri", attr(obj, "uri", "")));
                objectsArr.add(objInfo);
            }
            if (objectsArr.size() > 0) {
                result.add("objects", objectsArr);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAtcWorklist failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse inactive objects list XML.
     */
    public static JsonObject parseInactiveObjects(String xml) {
        JsonObject result = new JsonObject();
        JsonArray objects = new JsonArray();
        result.add("inactiveObjects", objects);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Look for entry or inactiveObject elements
            NodeList entries = doc.getElementsByTagName("entry");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("inactiveObject");
            }
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagNameNS(NS_ADT_CORE, "objectReference");
            }
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("objectReference");
            }

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", attr(entry, "adtcore:name", attr(entry, "name", childText(entry, "title", ""))));
                obj.addProperty("type", attr(entry, "adtcore:type", attr(entry, "type", "")));
                obj.addProperty("uri", attr(entry, "adtcore:uri", attr(entry, "uri", childAttr(entry, "link", "href", ""))));
                obj.addProperty("description", attr(entry, "adtcore:description", attr(entry, "description", childText(entry, "summary", ""))));
                obj.addProperty("user", attr(entry, "adtcore:responsible", attr(entry, "responsible", "")));
                objects.add(obj);
            }

            result.addProperty("count", objects.size());

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseInactiveObjects failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse object structure metadata XML.
     */
    public static JsonObject parseObjectStructure(String xml) {
        JsonObject result = new JsonObject();

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();

            // Extract basic info from root
            result.addProperty("name", attr(root, "adtcore:name", attr(root, "name", "")));
            result.addProperty("type", attr(root, "adtcore:type", attr(root, "type", "")));
            result.addProperty("description", attr(root, "adtcore:description", attr(root, "description", "")));
            result.addProperty("version", attr(root, "adtcore:version", attr(root, "version", "")));
            result.addProperty("createdBy", attr(root, "adtcore:createdBy", ""));
            result.addProperty("changedBy", attr(root, "adtcore:changedBy", ""));
            result.addProperty("masterLanguage", attr(root, "adtcore:masterLanguage", ""));

            // Package reference
            NodeList pkgRefs = doc.getElementsByTagName("packageRef");
            if (pkgRefs.getLength() == 0) {
                pkgRefs = doc.getElementsByTagNameNS(NS_ADT_CORE, "packageRef");
            }
            if (pkgRefs.getLength() > 0) {
                Element pkg = (Element) pkgRefs.item(0);
                result.addProperty("packageName", attr(pkg, "adtcore:name", attr(pkg, "name", "")));
            }

            // For classes - get includes (definitions, implementations, etc.)
            JsonArray includes = new JsonArray();
            NodeList includeNodes = doc.getElementsByTagName("include");
            for (int i = 0; i < includeNodes.getLength(); i++) {
                Element inc = (Element) includeNodes.item(i);
                JsonObject incObj = new JsonObject();
                incObj.addProperty("name", attr(inc, "adtcore:name", attr(inc, "name", "")));
                incObj.addProperty("type", attr(inc, "adtcore:type", attr(inc, "type", "")));
                incObj.addProperty("includeType", attr(inc, "includeType", attr(inc, "class:includeType", "")));
                String uri = attr(inc, "adtcore:uri", attr(inc, "uri", ""));
                incObj.addProperty("uri", uri);

                // Source URI for accessing the code
                NodeList links = inc.getElementsByTagName("link");
                for (int j = 0; j < links.getLength(); j++) {
                    Element link = (Element) links.item(j);
                    String rel = attr(link, "rel", "");
                    if (rel.contains("source") || rel.contains("main")) {
                        incObj.addProperty("sourceUri", attr(link, "href", ""));
                        break;
                    }
                }

                includes.add(incObj);
            }
            if (includes.size() > 0) {
                result.add("includes", includes);
            }

            // For function groups - get function modules
            JsonArray functions = new JsonArray();
            NodeList funcNodes = doc.getElementsByTagName("fmodule");
            if (funcNodes.getLength() == 0) {
                funcNodes = doc.getElementsByTagName("functionModule");
            }
            for (int i = 0; i < funcNodes.getLength(); i++) {
                Element func = (Element) funcNodes.item(i);
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", attr(func, "adtcore:name", attr(func, "name", "")));
                funcObj.addProperty("description", attr(func, "adtcore:description", attr(func, "description", "")));
                funcObj.addProperty("uri", attr(func, "adtcore:uri", attr(func, "uri", "")));
                functions.add(funcObj);
            }
            if (functions.size() > 0) {
                result.add("functionModules", functions);
            }

            // Links for navigation
            JsonArray links = new JsonArray();
            NodeList linkNodes = doc.getElementsByTagName("link");
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagNameNS(NS_ATOM, "link");
            }
            for (int i = 0; i < linkNodes.getLength(); i++) {
                Element link = (Element) linkNodes.item(i);
                JsonObject linkObj = new JsonObject();
                linkObj.addProperty("rel", attr(link, "rel", ""));
                linkObj.addProperty("href", attr(link, "href", ""));
                linkObj.addProperty("type", attr(link, "type", ""));
                links.add(linkObj);
            }
            if (links.size() > 0) {
                result.add("links", links);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseObjectStructure failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse ABAP documentation response (HTML/XML mixed content).
     * Extracts readable text from the documentation.
     */
    public static String parseAbapDocu(String content) {
        if (isBlank(content)) return "";

        StringBuilder result = new StringBuilder();

        try {
            // If it's XML, try to parse it
            if (content.trim().startsWith("<?xml") || content.trim().startsWith("<")) {
                try {
                    Document doc = parseDocument(content);

                    // Look for documentation text in various elements
                    NodeList docuNodes = doc.getElementsByTagName("documentation");
                    if (docuNodes.getLength() > 0) {
                        result.append(extractTextContent(docuNodes.item(0)));
                    }

                    // Also check for docu:documentation
                    docuNodes = doc.getElementsByTagName("docu:documentation");
                    if (docuNodes.getLength() > 0) {
                        result.append(extractTextContent(docuNodes.item(0)));
                    }

                    // Check for shortText elements
                    NodeList shortTexts = doc.getElementsByTagName("shortText");
                    for (int i = 0; i < shortTexts.getLength(); i++) {
                        String text = shortTexts.item(i).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            if (result.length() > 0) result.append("\n");
                            result.append(text.trim());
                        }
                    }

                    // Check for longText elements
                    NodeList longTexts = doc.getElementsByTagName("longText");
                    for (int i = 0; i < longTexts.getLength(); i++) {
                        String text = longTexts.item(i).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            if (result.length() > 0) result.append("\n\n");
                            result.append(text.trim());
                        }
                    }

                    // If still empty, try to get all text content
                    if (result.length() == 0) {
                        result.append(cleanHtmlTags(doc.getDocumentElement().getTextContent()));
                    }
                } catch (Exception e) {
                    // If XML parsing fails, treat as HTML/text
                    result.append(cleanHtmlTags(content));
                }
            } else {
                // Plain text or HTML
                result.append(cleanHtmlTags(content));
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAbapDocu failed: " + e.getMessage());
            // Return cleaned content as fallback
            return cleanHtmlTags(content);
        }

        return result.toString().trim();
    }

    /**
     * Extract text content from a node, preserving some structure.
     */
    private static String extractTextContent(Node node) {
        if (node == null) return "";

        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append(" ");
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = child.getNodeName().toLowerCase();
                // Add newlines for block elements
                if (tagName.equals("p") || tagName.equals("br") || tagName.equals("div")
                        || tagName.equals("li") || tagName.equals("tr")) {
                    sb.append("\n");
                }
                sb.append(extractTextContent(child));
                if (tagName.equals("p") || tagName.equals("div") || tagName.equals("li")) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Remove HTML tags and clean up text.
     */
    private static String cleanHtmlTags(String html) {
        if (html == null) return "";

        // Replace common HTML entities
        String text = html
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // Replace block elements with newlines
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)</tr>", "\n");
        text = text.replaceAll("(?i)<li>", "• ");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Clean up whitespace
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\n ", "\n");
        text = text.replaceAll(" \n", "\n");
        text = text.replaceAll("\n{3,}", "\n\n");

        return text.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
