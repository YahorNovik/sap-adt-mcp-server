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

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PREF_MCP_PORT, 3000);
        store.setDefault(PREF_AUTO_START, false);
    }
}
