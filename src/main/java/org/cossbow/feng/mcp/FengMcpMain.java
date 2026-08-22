package org.cossbow.feng.mcp;

import java.io.IOException;

public class FengMcpMain {

    public static void main(String[] args) {
        var server = new FengMcpServer();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Fēng MCP server error: " + e.getMessage());
            System.exit(1);
        }
    }
}
