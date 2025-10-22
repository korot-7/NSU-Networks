package client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FileSender {
    private static final int BUFFER_SIZE = 4096;
    private final String host;
    private final int port;
    private final File file;

    public static class FileInfo {
        public String filename;
        public long size;

        public FileInfo(String filename, long size) {
            this.filename = filename;
            this.size = size;
        }
    }

    public FileSender(String host, int port, File file) {
        this.host = host;
        this.port = port;
        this.file = file;
    }

    public void sendFile() throws IOException {
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             FileInputStream fileIn = new FileInputStream(file)) {

            writer.write("INFO\n");
            writer.flush();

            ObjectMapper mapper = new ObjectMapper();
            FileInfo fileInfo = new FileInfo(file.getName(), file.length());
            String json = mapper.writeValueAsString(fileInfo);
            writer.write(json + "\n");
            writer.flush();

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            socket.shutdownOutput();

            System.out.println("File send");

            String response = reader.readLine();
            if ("STATUS OK".equals(response)) {
                System.out.println("File send successful.");
            } else {
                System.out.println("File send failed. Server response: " + response);
            }

        } catch (IOException e) {
            throw new IOException("File send error: " + e.getMessage(), e);
        }
    }
}
