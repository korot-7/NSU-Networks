package server;

import java.io.File;
import java.io.IOException;

public class ServerController {
    private final int port;
    private final File uploadsDir = new File("uploads");

    public ServerController(String[] args) {
        port = Integer.parseInt(args[0]);

        if (!uploadsDir.exists()) {
            boolean ok = uploadsDir.mkdir();
            if (!ok) {
                System.err.println("Failed to create uploads directory at: " + uploadsDir.getAbsolutePath());
                System.exit(1);
            }
        } else if (!uploadsDir.isDirectory()) {
            System.err.println("'uploads' exists but is not a directory: " + uploadsDir.getAbsolutePath());
            System.exit(1);
        }
    }

    public void start() {
        FileServer server = new FileServer(port, uploadsDir);
        try {
            server.startServer();
        } catch (IOException e) {
            System.err.println("Server failed: " + e.getMessage());
        }
    }
}