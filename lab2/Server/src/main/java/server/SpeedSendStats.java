package server;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static server.ClientHandler.BYTES_TO_MB;
import static server.ClientHandler.MS_TO_SECONDS;
import static server.ClientHandler.STATS_INTERVAL_MS;

public class SpeedSendStats implements Runnable {
    private final String clientInfo;
    private final AtomicLong totalBytesReceived;
    private final long expectedFileSize;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private long lastReportTime;
    private long lastReportBytes;
    private final long startTime;

    public SpeedSendStats(String clientInfo, AtomicLong totalBytesReceived, long expectedFileSize) {
        this.clientInfo = clientInfo;
        this.totalBytesReceived = totalBytesReceived;
        this.expectedFileSize = expectedFileSize;

        this.lastReportTime = System.currentTimeMillis();
        this.lastReportBytes = 0;
        this.startTime = this.lastReportTime;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(STATS_INTERVAL_MS);
            } catch (InterruptedException e) {
                stop();
            }

            long now = System.currentTimeMillis();
            long currentBytes = totalBytesReceived.get();

            long diffBytes = currentBytes - lastReportBytes;
            long diffTime = now - lastReportTime;

            double instantSpeedMBs = 0;
            if (diffTime > 0)
                instantSpeedMBs = (diffBytes / BYTES_TO_MB) / (diffTime / MS_TO_SECONDS);

            double totalMB = currentBytes / BYTES_TO_MB;
            double totalExpectedMB = expectedFileSize / BYTES_TO_MB;
            double totalTime = (now - startTime) / MS_TO_SECONDS;
            double avgSpeedMBs = totalTime > 0 ? totalMB / totalTime : 0;

            System.out.printf(
                    "Client %s: Instant Speed = %.2f MB/s, Average Speed = %.2f MB/s. Received = %.2f MB (receivedAll %.2f / expected %.2f MB, time: %.2fs)\n",
                    clientInfo, instantSpeedMBs, avgSpeedMBs, diffBytes / BYTES_TO_MB, totalMB, totalExpectedMB, totalTime
            );

            lastReportTime = now;
            lastReportBytes = currentBytes;
        }

    }

    public void stop() {
        running.set(false);
    }
}
