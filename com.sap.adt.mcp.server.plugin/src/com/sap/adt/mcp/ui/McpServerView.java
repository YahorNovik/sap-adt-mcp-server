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

        appendOutput("SAP ADT MCP Server\n");
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

            connectToSap();
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
        try {
            String homeDir = System.getProperty("user.home");
            File claudeDir = new File(homeDir, ".claude");
            if (!claudeDir.exists()) {
                claudeDir.mkdirs();
            }

            File configFile = new File(claudeDir, "mcp_servers.json");

            // Read existing config or create new one
            com.google.gson.JsonObject config = new com.google.gson.JsonObject();
            if (configFile.exists()) {
                try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
                    config = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    appendOutput("Updating existing MCP config...\n");
                } catch (Exception e) {
                    // If parsing fails, start fresh
                    config = new com.google.gson.JsonObject();
                }
            }

            // Add/update sap-adt entry
            com.google.gson.JsonObject sapAdt = new com.google.gson.JsonObject();
            sapAdt.addProperty("url", "http://localhost:" + DEFAULT_PORT + "/mcp");
            config.add("sap-adt", sapAdt);

            // Add sap-docs (SAP documentation search) if not already present
            if (!config.has("sap-docs")) {
                com.google.gson.JsonObject sapDocs = new com.google.gson.JsonObject();
                sapDocs.addProperty("url", "https://mcp-sap-docs.marianzeis.de/mcp");
                config.add("sap-docs", sapDocs);
                appendOutput("Added SAP documentation MCP server (mcp-sap-docs).\n");
            }

            // Write merged config with pretty printing
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(gson.toJson(config));
            }

            appendOutput("MCP config written to: " + configFile.getAbsolutePath() + "\n");
        } catch (IOException e) {
            appendOutput("WARNING: Could not write MCP config: " + e.getMessage() + "\n");
        }
    }

    private void launchClaudeCode() {
        try {
            // Try to open Eclipse's built-in terminal view
            org.eclipse.ui.IWorkbenchPage page = getSite().getPage();

            // Try TM Terminal first (if available)
            try {
                org.eclipse.ui.IViewPart terminalView = page.showView(
                        "org.eclipse.tm.terminal.view.ui.TerminalsView",
                        null,
                        org.eclipse.ui.IWorkbenchPage.VIEW_ACTIVATE);

                if (terminalView != null) {
                    appendOutput("Opened Terminal view. Please run 'claude' command.\n");
                    appendOutput("TIP: Copy and paste: claude\n");

                    // Copy "claude" to clipboard for easy pasting
                    org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
                    org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
                    clipboard.setContents(new Object[]{"claude"}, new org.eclipse.swt.dnd.Transfer[]{textTransfer});
                    clipboard.dispose();
                    appendOutput("'claude' copied to clipboard - just paste (Cmd+V / Ctrl+V) in terminal.\n");
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
            pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "claude");
        } else {
            // Linux: Try common terminals
            String[] terminals = {"gnome-terminal", "konsole", "xfce4-terminal", "xterm"};
            String terminal = null;
            for (String t : terminals) {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"which", t});
                    if (p.waitFor() == 0) {
                        terminal = t;
                        break;
                    }
                } catch (Exception e) {
                    // Continue trying
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
     */
    private class ConnectionDialog extends Dialog {
        private Text urlText;
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

            new Label(container, SWT.NONE).setText("SAP URL:");
            urlText = new Text(container, SWT.BORDER);
            urlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            urlText.setText(sapUrl != null ? sapUrl : "https://host:44300");

            new Label(container, SWT.NONE).setText("User:");
            userText = new Text(container, SWT.BORDER);
            userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            userText.setText(sapUser != null ? sapUser : "");

            new Label(container, SWT.NONE).setText("Password:");
            passwordText = new Text(container, SWT.BORDER | SWT.PASSWORD);
            passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            new Label(container, SWT.NONE).setText("Client:");
            clientText = new Text(container, SWT.BORDER);
            clientText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            clientText.setText(sapClient != null ? sapClient : "100");

            new Label(container, SWT.NONE).setText("Language:");
            languageText = new Text(container, SWT.BORDER);
            languageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            languageText.setText(sapLanguage != null ? sapLanguage : "EN");

            return container;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            createButton(parent, IDialogConstants.OK_ID, "Connect", true);
            createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        }

        @Override
        protected void okPressed() {
            url = urlText.getText().trim();
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
