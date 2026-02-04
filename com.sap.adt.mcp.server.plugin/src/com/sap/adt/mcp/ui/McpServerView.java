package com.sap.adt.mcp.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

import com.sap.adt.mcp.server.McpServer;

/**
 * Eclipse view that manages the MCP Server and provides a terminal
 * for running Claude Code.
 */
public class McpServerView extends ViewPart {

    public static final String ID = "com.sap.adt.mcp.server.view";

    private static final int DEFAULT_PORT = 3000;

    private McpServer mcpServer;
    private Process claudeProcess;
    private Thread outputThread;

    private Label statusLabel;
    private Button startButton;
    private Button launchClaudeButton;
    private StyledText outputText;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // Status bar
        Composite statusBar = new Composite(parent, SWT.NONE);
        statusBar.setLayout(new GridLayout(4, false));
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setText("MCP Server: Stopped");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        startButton = new Button(statusBar, SWT.PUSH);
        startButton.setText("Start Server");
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
        appendOutput("1. Click 'Start Server' to start the MCP server\n");
        appendOutput("2. Click 'Launch Claude Code' to open Claude in terminal\n");
        appendOutput("   Or run manually: claude --mcp-server http://localhost:" + DEFAULT_PORT + "/mcp\n\n");

        // Initialize MCP server
        mcpServer = new McpServer(DEFAULT_PORT);
        mcpServer.setStatusListener((running, message) -> {
            Display.getDefault().asyncExec(() -> {
                statusLabel.setText("MCP Server: " + (running ? "Running" : "Stopped"));
                startButton.setText(running ? "Stop Server" : "Start Server");
                launchClaudeButton.setEnabled(running);
                appendOutput(message + "\n");
            });
        });

        // Register tools (placeholder - will be populated from SAP connection)
        registerDefaultTools();
    }

    private void registerDefaultTools() {
        // TODO: Register actual SAP tools when connected to a system
        // For now, just log that we'd register tools here
        appendOutput("Tools will be registered when connected to SAP system.\n");
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

    /**
     * Writes the MCP server config to ~/.claude/mcp_servers.json
     * so Claude Code auto-discovers our server.
     */
    private void writeMcpConfig() {
        try {
            String homeDir = System.getProperty("user.home");
            File claudeDir = new File(homeDir, ".claude");
            if (!claudeDir.exists()) {
                claudeDir.mkdirs();
            }

            File configFile = new File(claudeDir, "mcp_servers.json");
            String config = "{\n"
                    + "  \"sap-adt\": {\n"
                    + "    \"url\": \"http://localhost:" + DEFAULT_PORT + "/mcp\"\n"
                    + "  }\n"
                    + "}\n";

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config);
            }

            appendOutput("MCP config written to: " + configFile.getAbsolutePath() + "\n");
        } catch (IOException e) {
            appendOutput("WARNING: Could not write MCP config: " + e.getMessage() + "\n");
        }
    }

    private void launchClaudeCode() {
        if (claudeProcess != null && claudeProcess.isAlive()) {
            appendOutput("Claude Code is already running.\n");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("claude");
            pb.redirectErrorStream(true);
            claudeProcess = pb.start();

            appendOutput("\n--- Claude Code Started ---\n\n");

            // Read output in background thread
            outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(claudeProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String output = line;
                        Display.getDefault().asyncExec(() -> appendOutput(output + "\n"));
                    }
                } catch (IOException e) {
                    // Process ended
                }
                Display.getDefault().asyncExec(() ->
                        appendOutput("\n--- Claude Code Exited ---\n"));
            });
            outputThread.setDaemon(true);
            outputThread.start();

        } catch (IOException e) {
            appendOutput("ERROR: Failed to launch Claude Code: " + e.getMessage() + "\n");
            appendOutput("Make sure 'claude' CLI is installed and in your PATH.\n");
            appendOutput("Install: npm install -g @anthropic-ai/claude-code\n");
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
        super.dispose();
    }
}
