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

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
