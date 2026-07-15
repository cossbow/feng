package org.cossbow.feng.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;

public class FengLspMain {

    public static void main(String[] args) {
        var server = new FengLanguageServer();
        var launcher = LSPLauncher.createServerLauncher(
                server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        try {
            launcher.startListening().get();
        } catch (Exception e) {
            System.err.println("Fēng LSP server error: " + e.getMessage());
            System.exit(1);
        }
    }
}
