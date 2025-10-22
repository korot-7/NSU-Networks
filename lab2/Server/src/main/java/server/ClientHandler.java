package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final File uploadsDir;
    private final String clientInfo;
    public static final long STATS_INTERVAL_MS = 3000;
    private static final int BUFFER_BYTES_SIZE = 8 * 1024;
    public static final double BYTES_TO_MB = 1024.0 * 1024.0;
    public static final double MS_TO_SECONDS = 1000.0;

    public ClientHandler(Socket socket, File uploadsDir, String clientInfo) {
        this.socket = socket;
        this.uploadsDir = uploadsDir;
        this.clientInfo = clientInfo;
    }

    public static class FileInfo {
        public String filename;
        public long size;
    }

    private String readLineFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            out.write(b);
        }
        if (out.size() == 0 && b == -1) return null;
        return out.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void run() {
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream();
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            String line1 = readLineFromStream(in);
            if (line1 == null || !line1.equals("INFO")) {
                writer.write("ERROR\n");
                writer.flush();
                return;
            }

            String json = readLineFromStream(in);
            if (json == null || json.isEmpty()) {
                writer.write("ERROR\n");
                writer.flush();
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            FileInfo info = mapper.readValue(json, FileInfo.class);

            if (info.filename == null || info.filename.isEmpty() || info.size <= 0) {
                writer.write("ERROR\n");
                writer.flush();
                return;
            }

            String fileName = info.filename;
            long expectedFileSize = info.size;

            File file = new File(uploadsDir, fileName);

            AtomicLong totalBytesReceived = new AtomicLong(0);

            String infoClientFile = clientInfo + " file - " + fileName;

            SpeedSendStats stats = new SpeedSendStats(infoClientFile, totalBytesReceived, expectedFileSize);
            Thread statsThread = new Thread(stats, infoClientFile);
            statsThread.start();


            byte[] buffer = new byte[BUFFER_BYTES_SIZE];
            long startTime = System.currentTimeMillis();

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, read);
                    totalBytesReceived.addAndGet(read);
                }
            }
            catch (IOException e) {
                System.err.println("Error: " + e);
            }
            finally {
                stats.stop();
                statsThread.interrupt();
                statsThread.join();
            }

            long endTime = System.currentTimeMillis();

            long totalBytes = totalBytesReceived.get();
            double totalMB = totalBytes / BYTES_TO_MB;
            double totalTime = (endTime - startTime) / MS_TO_SECONDS;
            double avgSpeedMBs = totalMB / totalTime;

            System.out.printf(
                    "Client %s, result: Average Speed = %.2f MB/s (received %.2f MB in %.2fs)\n",
                    infoClientFile, avgSpeedMBs, totalMB, totalTime
            );

            boolean success = (totalBytes == expectedFileSize);
            if (success) {
                writer.write("STATUS OK\n");
                System.out.printf("Client %s: file saved successfully: %s (%.2f MB)%n",
                        clientInfo, file, totalMB);
            } else {
                writer.write("ERROR\n");
                System.err.printf("Client %s: send failed (expected %.2f MB, got %.2f MB)%n",
                        clientInfo, expectedFileSize / BYTES_TO_MB, totalMB);
            }
            writer.flush();


        } catch (Exception e) {
            System.err.printf("Error with client %s: %s%n", clientInfo, e.getMessage());
        } finally {
            System.out.println("Disconnected: " + clientInfo);
        }
    }
}
