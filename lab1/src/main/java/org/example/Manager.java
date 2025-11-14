package org.example;

import java.net.*;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Manager {
    private final int PORT = 8888;
    private final int SEND_INTERVAL_MS = 2000;
    private final int CLEAN_INTERVAL_MS = 5000;
    private final int MAX_TIME_MS = 10000;
    private final int BYTES_SIZE = 1024;
    private final String NETWORK_ADDRESS_IPV4 = "172.20.10.";
    private final String NETWORK_ADDRESS_IPV6 = "fe80:";

    private final InetAddress multicastGroup;
    private final MulticastSocket multicastSocket;
    private final NetworkInterface networkInterface;

    private final String localAddress;
    private final String processID;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);

    private final ConcurrentHashMap<String, Long> aliveApps = new ConcurrentHashMap<>();
    private boolean hasChanges = false;

    public Manager(String multicastGroupAddress) throws Exception {

        InetAddress inetAddress = InetAddress.getByName(multicastGroupAddress);
        if (!inetAddress.isMulticastAddress()) {
            System.err.println("This address is not a multicast address");
            System.exit(1);
        }

        multicastGroup = inetAddress;
        processID = UUID.randomUUID().toString();


        multicastSocket = new MulticastSocket(null);
        multicastSocket.setReuseAddress(true);
        multicastSocket.bind(new InetSocketAddress(PORT));


        networkInterface = findNetworkInterface(multicastGroup);
        if (networkInterface == null){
            multicastSocket.close();
            System.err.println("No network interface found");
            System.exit(1);
        }

        multicastSocket.setNetworkInterface(networkInterface);
        multicastSocket.joinGroup(new InetSocketAddress(multicastGroup, PORT), networkInterface);

        boolean needIPv6 = inetAddress instanceof Inet6Address;
        localAddress = getLocalAddressForInterface(networkInterface, needIPv6);
        if (localAddress == null){
            multicastSocket.close();
            System.err.println("No local address found");
            System.exit(1);
        }


        System.out.println("Multicast group: " + multicastGroupAddress + " is " + (multicastGroup instanceof Inet4Address ? "IPv4" : "IPv6") + " protocol");
        System.out.println("Local address: " + localAddress);
        System.out.println("Process ID: " + processID);
    }

    private static String getLocalAddressForInterface(NetworkInterface ni, boolean needIPv6) {
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
            InetAddress addr = ia.getAddress();
            if (addr == null)
                continue;

            if (needIPv6 && addr instanceof Inet6Address) {
                return addr.getHostAddress();
            }

            if (!needIPv6 && addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    private NetworkInterface findNetworkInterface(InetAddress group) throws SocketException {
        boolean needIPv6 = group instanceof Inet6Address;

        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                continue;

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                if (addr == null) continue;

                if (addr instanceof Inet4Address && !needIPv6) {
                    String localAddressForInterface = getLocalAddressForInterface(ni, needIPv6);
                    if (localAddressForInterface == null) continue;
                    if (localAddressForInterface.startsWith(NETWORK_ADDRESS_IPV4)) {
                        System.out.println("Detected interface: " + ni.getDisplayName() +
                                " (" + addr.getHostAddress() + ")");
                        return ni;
                    }
                }
                if (addr instanceof Inet6Address && needIPv6) {
                    String localAddressForInterface = getLocalAddressForInterface(ni, needIPv6);
                    if (localAddressForInterface == null) continue;
                    if (localAddressForInterface.startsWith(NETWORK_ADDRESS_IPV6)) {
                        if (ni.getDisplayName().contains("Virtual")) continue;
                        System.out.println("Detected interface: " + ni.getDisplayName() +
                                " (" + addr.getHostAddress() + ")");
                        return ni;
                    }
                }
            }
        }

        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                continue;

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                if (ia.getAddress() instanceof Inet4Address && !needIPv6) {
                    return ni;
                }
                if (ia.getAddress() instanceof Inet6Address && needIPv6) {
                    return ni;
                }
            }
        }
        return null;
    }

    public void start() {

        executor.execute(this::receiverTask);

        executor.scheduleAtFixedRate(
                this::senderTask, 0, SEND_INTERVAL_MS, TimeUnit.MILLISECONDS);

        executor.scheduleAtFixedRate(
                this::cleanerTask, 0, CLEAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {

        executor.shutdownNow();

        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                multicastSocket.leaveGroup(new InetSocketAddress(multicastGroup, PORT), networkInterface);
                multicastSocket.close();
                System.out.println("Socket closed, executor stopped.");
            } catch (Exception e) {
                System.err.println("Error stopping: " + e.getMessage());
            }
        }
    }

    private void senderTask() {
        try {
            String message = "Hello@" + localAddress + "@" + processID;
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, multicastGroup, PORT);

            multicastSocket.send(packet);

        } catch (Exception e) {
            stop();
        }
    }


    private void receiverTask() {
        byte[] buffer = new byte[BYTES_SIZE];

        while (!Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                multicastSocket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength()).trim();

                if (message.startsWith("Hello@")) {
                    String[] parts = message.split("@");
                    if (parts.length >= 3) {
                        String remoteIp = packet.getAddress().getHostAddress();
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

            } catch (Exception e) {
                stop();
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
            stop();
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