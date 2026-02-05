package com.sap.adt.mcp.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.sap.adt.mcp.Activator;

/**
 * Preference page for SAP ADT MCP Server for Claude Code settings.
 */
public class McpPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public McpPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Settings for the SAP ADT MCP Server for Claude Code.");
    }

    @Override
    public void createFieldEditors() {
        IntegerFieldEditor portEditor = new IntegerFieldEditor(
                PreferenceInitializer.PREF_MCP_PORT,
                "MCP Server Port:",
                getFieldEditorParent());
        portEditor.setValidRange(1024, 65535);
        addField(portEditor);

        addField(new BooleanFieldEditor(
                PreferenceInitializer.PREF_AUTO_START,
                "Auto-start MCP Server when Eclipse starts",
                getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        // Nothing to initialize
    }
}
