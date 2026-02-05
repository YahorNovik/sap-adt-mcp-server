package com.sap.adt.mcp.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.server.McpServer;
import com.sap.adt.mcp.tools.*;

/**
 * Eclipse view that manages the MCP Server and provides a terminal
 * for running Claude Code.
 */
public class McpServerView extends ViewPart {

    public static final String ID = "com.sap.adt.mcp.server.view";

    private static final int DEFAULT_PORT = 3000;

    private McpServer mcpServer;
    private AdtRestClient adtClient;
    private Process claudeProcess;
    private Thread outputThread;

    private Label statusLabel;
    private Button connectButton;
    private Button startButton;
    private Button launchClaudeButton;
    private StyledText outputText;

    // Connection details
    private String sapUrl;
    private String sapUser;
    private String sapPassword;
    private String sapClient;
    private String sapLanguage = "EN";

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // Status bar
        Composite statusBar = new Composite(parent, SWT.NONE);
        statusBar.setLayout(new GridLayout(5, false));
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setText("SAP: Not Connected | MCP: Stopped");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        connectButton = new Button(statusBar, SWT.PUSH);
        connectButton.setText("Connect SAP");
        connectButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showConnectionDialog();
            }
        });

        startButton = new Button(statusBar, SWT.PUSH);
        startButton.setText("Start Server");
        startButton.setEnabled(false);
        startButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                toggleServer();
            }
        });

        launchClaudeButton = new Button(statusBar, SWT.PUSH);
        launchClaudeButton.setText("Launch Claude Code");
        launchClaudeButton.setEnabled(false);
        launchClaudeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                launchClaudeCode();
            }
        });

        Button clearButton = new Button(statusBar, SWT.PUSH);
        clearButton.setText("Clear");
        clearButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                outputText.setText("");
            }
        });

        // Output/terminal area
        outputText = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        outputText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        outputText.setEditable(false);
        outputText.setFont(parent.getDisplay().getSystemFont());

        appendOutput("SAP ADT MCP Server for Claude Code\n");
        appendOutput("==================\n\n");
        appendOutput("This plugin exposes SAP ADT tools via MCP protocol for Claude Code.\n\n");
        appendOutput("1. Click 'Connect SAP' to enter connection details\n");
        appendOutput("2. Click 'Start Server' to start the MCP server\n");
        appendOutput("3. Click 'Launch Claude Code' to open Claude in terminal\n");
        appendOutput("   Or run manually: claude --mcp-server http://localhost:" + DEFAULT_PORT + "/mcp\n\n");

        // Initialize MCP server
        mcpServer = new McpServer(DEFAULT_PORT);
        mcpServer.setStatusListener((running, message) -> {
            Display.getDefault().asyncExec(() -> {
                updateStatusLabel();
                startButton.setText(running ? "Stop Server" : "Start Server");
                launchClaudeButton.setEnabled(running);
                appendOutput(message + "\n");
            });
        });
    }

    private void showConnectionDialog() {
        ConnectionDialog dialog = new ConnectionDialog(getSite().getShell());
        if (dialog.open() == Dialog.OK) {
            sapUrl = dialog.getUrl();
            sapUser = dialog.getUser();
            sapPassword = dialog.getPassword();
            sapClient = dialog.getSapClient();
            sapLanguage = dialog.getLanguage();

            // Save connection details (never password) for next time
            saveConnectionHistory();

            connectToSap();
        }
    }

    private void saveConnectionHistory() {
        try {
            org.eclipse.jface.preference.IPreferenceStore store =
                    com.sap.adt.mcp.Activator.getDefault().getPreferenceStore();

            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_URL, sapUrl);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_USER, sapUser);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_CLIENT, sapClient);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_LANGUAGE, sapLanguage);

            // Add URL to history (keep last 10, semicolon-separated)
            String history = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY);
            java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
            urls.add(sapUrl); // most recent first
            if (history != null && !history.isEmpty()) {
                for (String u : history.split(";")) {
                    if (!u.trim().isEmpty()) urls.add(u.trim());
                }
            }
            // Keep max 10
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String u : urls) {
                if (count++ >= 10) break;
                if (sb.length() > 0) sb.append(";");
                sb.append(u);
            }
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY, sb.toString());
        } catch (Exception e) {
            // Preference store not available, ignore
        }
    }

    private void connectToSap() {
        appendOutput("Connecting to SAP at " + sapUrl + "...\n");

        try {
            adtClient = new AdtRestClient(sapUrl, sapUser, sapPassword, sapClient, sapLanguage, true);
            adtClient.login();

            appendOutput("Successfully connected to SAP!\n");
            appendOutput("Registering SAP tools...\n");

            registerSapTools();

            appendOutput("Registered " + mcpServer.getToolCount() + " tools.\n\n");
            startButton.setEnabled(true);
            updateStatusLabel();

        } catch (Exception e) {
            appendOutput("ERROR: Failed to connect to SAP: " + e.getMessage() + "\n");
            adtClient = null;
        }
    }

    private void registerSapTools() {
        List<McpTool> tools = new ArrayList<>();

        // Core object operations
        tools.add(new SearchObjectTool(adtClient));
        tools.add(new GetSourceTool(adtClient));
        tools.add(new SetSourceTool(adtClient));
        tools.add(new ObjectStructureTool(adtClient));

        // Lock management
        tools.add(new LockTool(adtClient));
        tools.add(new UnlockTool(adtClient));

        // Syntax and activation
        tools.add(new SyntaxCheckTool(adtClient));
        tools.add(new ActivateTool(adtClient));
        tools.add(new InactiveObjectsTool(adtClient));

        // Object creation
        tools.add(new CreateObjectTool(adtClient));

        // Testing and quality
        tools.add(new RunUnitTestTool(adtClient));
        tools.add(new AtcRunTool(adtClient));

        // Analysis
        tools.add(new UsageReferencesTool(adtClient));
        tools.add(new SqlQueryTool(adtClient));

        // Documentation
        tools.add(new AbapDocuTool(adtClient));

        mcpServer.registerTools(tools);
    }

    private void updateStatusLabel() {
        String sapStatus = adtClient != null && adtClient.isLoggedIn() ? "Connected" : "Not Connected";
        String mcpStatus = mcpServer != null && mcpServer.isRunning() ? "Running" : "Stopped";
        statusLabel.setText("SAP: " + sapStatus + " | MCP: " + mcpStatus);
    }

    private void toggleServer() {
        if (mcpServer.isRunning()) {
            mcpServer.stop();
        } else {
            try {
                mcpServer.start();
                writeMcpConfig();
            } catch (IOException e) {
                appendOutput("ERROR: Failed to start server: " + e.getMessage() + "\n");
            }
        }
    }

    private void writeMcpConfig() {
        String adtUrl = "http://localhost:" + DEFAULT_PORT + "/mcp";
        String docsUrl = "https://mcp-sap-docs.marianzeis.de/mcp";

        appendOutput("\n");
        appendOutput("=== Run these commands to register MCP servers in Claude Code ===\n\n");
        appendOutput("claude mcp add --transport http --scope user sap-adt " + adtUrl + "\n");
        appendOutput("claude mcp add --transport http --scope user sap-docs " + docsUrl + "\n");
        appendOutput("\n");
        appendOutput("Then verify with: claude mcp list\n");
        appendOutput("================================================================\n\n");

        // Copy commands to clipboard
        String commands = "claude mcp add --transport http --scope user sap-adt " + adtUrl
                + "\nclaude mcp add --transport http --scope user sap-docs " + docsUrl;
        try {
            org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
            org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
            clipboard.setContents(new Object[]{commands}, new org.eclipse.swt.dnd.Transfer[]{textTransfer});
            clipboard.dispose();
            appendOutput("Commands copied to clipboard. Paste in your terminal.\n\n");
        } catch (Exception e) {
            // Clipboard not available, commands are still shown in output
        }
    }

    private void launchClaudeCode() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");

            // Determine the command to copy to clipboard
            String clipboardCmd = isWindows ? "wsl -- claude" : "claude";

            // Try to open Eclipse's built-in terminal view
            org.eclipse.ui.IWorkbenchPage page = getSite().getPage();

            try {
                org.eclipse.ui.IViewPart terminalView = page.showView(
                        "org.eclipse.tm.terminal.view.ui.TerminalsView",
                        null,
                        org.eclipse.ui.IWorkbenchPage.VIEW_ACTIVATE);

                if (terminalView != null) {
                    // Copy command to clipboard for easy pasting
                    org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
                    org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
                    clipboard.setContents(new Object[]{clipboardCmd}, new org.eclipse.swt.dnd.Transfer[]{textTransfer});
                    clipboard.dispose();

                    appendOutput("Opened Terminal view.\n");
                    appendOutput("Paste '" + clipboardCmd + "' (already in clipboard) to start Claude Code.\n");
                    if (isWindows) {
                        appendOutput("WSL is used because Claude Code requires a Unix environment on Windows.\n");
                    }
                    return;
                }
            } catch (org.eclipse.ui.PartInitException e) {
                // TM Terminal not available, try fallback
            }

            // Fallback: Open external terminal
            appendOutput("Eclipse Terminal not available. Opening external terminal...\n");
            openExternalTerminal();

        } catch (Exception e) {
            appendOutput("ERROR: Failed to launch terminal: " + e.getMessage() + "\n");
            appendOutput("You can run Claude Code manually: claude\n");
        }
    }

    private boolean isWslAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"wsl.exe", "--status"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void openExternalTerminal() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (os.contains("mac")) {
            String script = "tell application \"Terminal\"\n"
                    + "    activate\n"
                    + "    do script \"claude\"\n"
                    + "end tell";
            pb = new ProcessBuilder("osascript", "-e", script);
        } else if (os.contains("win")) {
            if (isWslAvailable()) {
                // Try Windows Terminal with WSL first (modern Windows)
                if (isCommandAvailable("wt.exe")) {
                    pb = new ProcessBuilder("wt.exe", "-p", "Ubuntu", "--", "wsl", "--", "claude");
                    appendOutput("Opening Windows Terminal with WSL...\n");
                } else {
                    // Fall back to wsl.exe directly in a new cmd window
                    pb = new ProcessBuilder("cmd", "/c", "start", "wsl.exe", "--", "claude");
                    appendOutput("Opening WSL terminal...\n");
                }
            } else {
                appendOutput("WARNING: WSL not found. Claude Code requires a Unix environment on Windows.\n");
                appendOutput("Install WSL: wsl --install\n");
                appendOutput("Then install Claude Code in WSL: npm install -g @anthropic-ai/claude-code\n");
                pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k",
                        "echo Claude Code requires WSL on Windows. Run: wsl --install");
            }
        } else {
            // Linux: Try common terminals
            String[] terminals = {"gnome-terminal", "konsole", "xfce4-terminal", "xterm"};
            String terminal = null;
            for (String t : terminals) {
                if (isCommandAvailable(t)) {
                    terminal = t;
                    break;
                }
            }
            if (terminal != null) {
                if (terminal.equals("gnome-terminal")) {
                    pb = new ProcessBuilder(terminal, "--", "claude");
                } else {
                    pb = new ProcessBuilder(terminal, "-e", "claude");
                }
            } else {
                throw new IOException("No terminal emulator found");
            }
        }

        pb.start();
        appendOutput("Launched Claude Code in external terminal.\n");
    }

    private boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process p;
            if (os.contains("win")) {
                p = Runtime.getRuntime().exec(new String[]{"where", command});
            } else {
                p = Runtime.getRuntime().exec(new String[]{"which", command});
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void appendOutput(String text) {
        if (outputText != null && !outputText.isDisposed()) {
            outputText.append(text);
            outputText.setTopIndex(outputText.getLineCount() - 1);
        }
    }

    @Override
    public void setFocus() {
        if (outputText != null && !outputText.isDisposed()) {
            outputText.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
        if (claudeProcess != null && claudeProcess.isAlive()) {
            claudeProcess.destroy();
        }
        if (adtClient != null) {
            adtClient.logout();
        }
        super.dispose();
    }

    /**
     * Connection dialog for SAP system details.
     * Loads previous connection details from preferences (except password).
     */
    private class ConnectionDialog extends Dialog {
        private org.eclipse.swt.widgets.Combo urlCombo;
        private Text userText;
        private Text passwordText;
        private Text clientText;
        private Text languageText;

        private String url;
        private String user;
        private String password;
        private String client;
        private String language;

        protected ConnectionDialog(Shell parentShell) {
            super(parentShell);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText("Connect to SAP System");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = (Composite) super.createDialogArea(parent);
            container.setLayout(new GridLayout(2, false));

            // Load saved values from preferences
            String savedUrl = "";
            String savedUser = "";
            String savedClient = "100";
            String savedLanguage = "EN";
            String[] urlHistory = new String[0];

            try {
                org.eclipse.jface.preference.IPreferenceStore store =
                        com.sap.adt.mcp.Activator.getDefault().getPreferenceStore();
                savedUrl = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_URL);
                savedUser = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_USER);
                savedClient = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_CLIENT);
                savedLanguage = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_LANGUAGE);
                String history = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY);
                if (history != null && !history.isEmpty()) {
                    urlHistory = history.split(";");
                }
            } catch (Exception e) {
                // Preferences not available
            }

            // Use saved values, fall back to current session values, then defaults
            String defaultUrl = !savedUrl.isEmpty() ? savedUrl : (sapUrl != null ? sapUrl : "");
            String defaultUser = !savedUser.isEmpty() ? savedUser : (sapUser != null ? sapUser : "");
            String defaultClient = !savedClient.isEmpty() ? savedClient : (sapClient != null ? sapClient : "100");
            String defaultLanguage = !savedLanguage.isEmpty() ? savedLanguage : (sapLanguage != null ? sapLanguage : "EN");

            new Label(container, SWT.NONE).setText("SAP URL:");
            urlCombo = new org.eclipse.swt.widgets.Combo(container, SWT.BORDER);
            urlCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            // Populate URL dropdown with history
            for (String histUrl : urlHistory) {
                if (!histUrl.trim().isEmpty()) {
                    urlCombo.add(histUrl.trim());
                }
            }
            urlCombo.setText(defaultUrl);

            new Label(container, SWT.NONE).setText("User:");
            userText = new Text(container, SWT.BORDER);
            userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            userText.setText(defaultUser);

            new Label(container, SWT.NONE).setText("Password:");
            passwordText = new Text(container, SWT.BORDER | SWT.PASSWORD);
            passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            new Label(container, SWT.NONE).setText("Client:");
            clientText = new Text(container, SWT.BORDER);
            clientText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            clientText.setText(defaultClient);

            new Label(container, SWT.NONE).setText("Language:");
            languageText = new Text(container, SWT.BORDER);
            languageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            languageText.setText(defaultLanguage);

            // Focus password field since other fields are pre-filled
            container.getDisplay().asyncExec(() -> passwordText.setFocus());

            return container;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            createButton(parent, IDialogConstants.OK_ID, "Connect", true);
            createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        }

        @Override
        protected void okPressed() {
            url = urlCombo.getText().trim();
            user = userText.getText().trim();
            password = passwordText.getText();
            client = clientText.getText().trim();
            language = languageText.getText().trim();
            super.okPressed();
        }

        public String getUrl() { return url; }
        public String getUser() { return user; }
        public String getPassword() { return password; }
        public String getSapClient() { return client; }
        public String getLanguage() { return language; }
    }
}
