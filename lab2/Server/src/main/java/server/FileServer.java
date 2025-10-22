package server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {
    private final int port;
    private final File uploadsDir;

    public FileServer(int port, File uploadsDir) {
        this.port = port;
        this.uploadsDir = uploadsDir;
    }

    public void startServer() throws IOException {
        System.out.printf("Server (address: %s) listening on port %d\n",
                InetAddress.getLocalHost().getHostAddress(), port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientInfo = clientSocket.getInetAddress().getHostAddress();
                    System.out.println("\nAccepted connection from " + clientInfo);

                    Thread thread = new Thread(new ClientHandler(clientSocket, uploadsDir, clientInfo));
                    thread.start();
                } catch (IOException e) {
                    System.err.println("Failed to accept new connection: " + e.getMessage());
                }
            }
        }
    }
}