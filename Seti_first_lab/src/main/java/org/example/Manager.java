package org.example;

import java.net.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Manager {
    private static final int PORT = 8888;
    private static final long SEND_INTERVAL_MS = 2000;
    private static final long CLEAN_INTERVAL_MS = 5000;
    private static final long MAX_TIME_MS = 10000;

    private final InetAddress multicastGroup;
    private MulticastSocket multicastSocket;
    private final String localAddress;
    private final String processID;

    private final ConcurrentHashMap<String, Long> aliveApps = new ConcurrentHashMap<>();

    private boolean hasChanges = false;

    public Manager(String multicastGroupAddress) throws Exception {

        InetAddress inetAddress = InetAddress.getByName(multicastGroupAddress);
        if (!inetAddress.isMulticastAddress()) {
            System.err.println("This address is not a multicast address");
            System.exit(1);
        }

        multicastGroup = inetAddress;
        localAddress = InetAddress.getLocalHost().getHostAddress();
        processID = UUID.randomUUID().toString();

        multicastSocket = new MulticastSocket(PORT);
        multicastSocket.joinGroup(multicastGroup);

        System.out.println("Multicast group: " + multicastGroupAddress + " is " + (multicastGroup instanceof Inet4Address ? "IPv4" : "IPv6") + " protocol");
        System.out.println("Local address: " + localAddress);
        System.out.println("Process ID: " + processID);
    }


    public void start() {

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);

        executor.execute(this::receiverTask);

        executor.scheduleAtFixedRate(
                this::senderTask, 0, SEND_INTERVAL_MS, TimeUnit.MILLISECONDS);

        executor.scheduleAtFixedRate(
                this::cleanerTask, 0, CLEAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void senderTask() {
        try {
            String message = "Hello:" + localAddress + ":" + processID;
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, multicastGroup, PORT);

            multicastSocket.send(packet);

        } catch (Exception e) {
            System.err.println("Sender error: " + e.getMessage());
        }
    }


    private void receiverTask() {
        byte[] buffer = new byte[1024];

        while (!Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                multicastSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                if (message.startsWith("Hello:")) {
                    String[] parts = message.split(":");
                    if (parts.length >= 3) {
                        String remoteIp = parts[1];
                        String remoteProcessID = parts[2];

                        if (remoteProcessID.equals(processID)) {
                            continue;
                        }

                        String appKey = remoteIp + " [" + remoteProcessID + "]";

                        long currentTime = System.currentTimeMillis();

                        boolean isNew = !aliveApps.containsKey(appKey);

                        aliveApps.put(appKey, currentTime);

                        if (isNew) {
                            hasChanges = true;
                            System.out.println("New app append: " + appKey);
                        }
                    }
                }

            } catch (SocketTimeoutException ignored) {
            }
            catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    System.err.println("Receiver error: " + e.getMessage());
                }
            }
        }
    }

    private void cleanerTask() {
        try {
            long currentTime = System.currentTimeMillis();

            boolean removedAny = aliveApps.entrySet().removeIf(entry -> {
                long timeSinceLastSeen = currentTime - entry.getValue();
                if (timeSinceLastSeen > MAX_TIME_MS) {
                    System.out.println("App delete: " + entry.getKey());
                    return true;
                }
                return false;
            });

            if (hasChanges || removedAny) {
                printAliveApps();
                hasChanges = false;
            }

        } catch (Exception e) {
            System.err.println("Cleaner error: " + e.getMessage());
        }
    }

    private void printAliveApps() {
        if (aliveApps.isEmpty()) {
            System.out.println("No alive another apps");
        } else {
            System.out.println("Alive apps (" + aliveApps.size() + "): " +
                    String.join(", ", aliveApps.keySet()));
        }
    }
}