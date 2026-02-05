package com.sap.adt.mcp.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.sap.adt.mcp.Activator;

/**
 * Initializes default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    public static final String PREF_MCP_PORT = "mcp.server.port";
    public static final String PREF_AUTO_START = "mcp.server.autostart";

    // Connection history (no password stored)
    public static final String PREF_LAST_URL = "sap.connection.lastUrl";
    public static final String PREF_LAST_USER = "sap.connection.lastUser";
    public static final String PREF_LAST_CLIENT = "sap.connection.lastClient";
    public static final String PREF_LAST_LANGUAGE = "sap.connection.lastLanguage";
    public static final String PREF_URL_HISTORY = "sap.connection.urlHistory";

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PREF_MCP_PORT, 3000);
        store.setDefault(PREF_AUTO_START, false);
        store.setDefault(PREF_LAST_URL, "");
        store.setDefault(PREF_LAST_USER, "");
        store.setDefault(PREF_LAST_CLIENT, "100");
        store.setDefault(PREF_LAST_LANGUAGE, "EN");
        store.setDefault(PREF_URL_HISTORY, "");
    }
}
