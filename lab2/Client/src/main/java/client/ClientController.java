package client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientController {
    private final int port;
    private final File file;
    private final String host;

    private static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;
    private static final int MAX_FILENAME_BYTES = 4096;

    public ClientController(String[] args) {
        host = args[0];
        port = Integer.parseInt(args[1]);

        Path path = Paths.get(args[2]).toAbsolutePath().normalize();

        if (!(Files.exists(path) &&  Files.isRegularFile(path) && Files.isReadable(path))) {
            System.err.println("The error file : " + path);
            System.exit(1);
        }


        file = path.toFile();


        String fileName = file.getName();
        int fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).length;
        if (fileNameSize > MAX_FILENAME_BYTES) {
            System.err.printf("File name is too long: %d bytes (max %d bytes)\n", fileNameSize, MAX_FILENAME_BYTES);
            System.exit(1);
        }

        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE) {
            System.err.printf("File is too large: %d bytes (max %d bytes)\n", fileSize, MAX_FILE_SIZE);
            System.exit(1);
        }

        System.out.println("File size: " + fileSize + " bytes");
    }


    public void start() {
        FileSender sender = new FileSender(host, port, file);
        try {
            sender.sendFile();
        } catch (IOException e) {
            System.err.println("Error during file sending: " + e.getMessage());
        }
    }
}
