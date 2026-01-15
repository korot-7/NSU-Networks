package lab4.snake.network;

import com.google.protobuf.InvalidProtocolBufferException;
import lab4.protobuf.SnakesProto;
import lab4.snake.util.Config;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.function.BiConsumer;

public class MulticastReceiver implements Runnable {
    private MulticastSocket socket;
    private InetSocketAddress groupAddress;
    private NetworkInterface networkInterface;
    private final BiConsumer<SnakesProto.GameMessage, InetSocketAddress> messageHandler;
    private volatile boolean running = true;
    private boolean joined = false;

    public MulticastReceiver(BiConsumer<SnakesProto.GameMessage, InetSocketAddress> messageHandler)
            throws IOException {
        this.messageHandler = messageHandler;

        InetAddress group = InetAddress.getByName(Config.MULTICAST_IP);
        groupAddress = new InetSocketAddress(group, Config.MULTICAST_PORT);

        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(Config.MULTICAST_PORT));
        socket.setSoTimeout(Config.SOCKET_TIMEOUT);

        networkInterface = findNetworkInterface();

        if (networkInterface == null) {
            System.err.println("No network interface available!");
            socket.close();
            return;
        }

        try {
            socket.setNetworkInterface(networkInterface);
            socket.joinGroup(groupAddress, networkInterface);
            joined = true;
            System.out.println("Successfully joined multicast group: " + Config.MULTICAST_IP);
        } catch (IOException e) {
            System.err.println("Could not join multicast group: " + e.getMessage());
        }
    }

    private NetworkInterface findNetworkInterface() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                continue;

            String name = ni.getDisplayName().toLowerCase();
            if (name.contains("docker") || name.contains("virtual") ||
                    name.contains("vmware") || name.contains("vbox"))
                continue;

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    System.out.println("Selected interface: " + ni.getDisplayName() +
                            " (" + addr.getHostAddress() + ")");
                    return ni;
                }
            }
        }
        return null;
    }

    @Override
    public void run() {
        if (!joined) {
            return;
        }

        byte[] buffer = new byte[Config.UDP_BUFFER_SIZE];

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetSocketAddress sender = new InetSocketAddress(
                        packet.getAddress(), packet.getPort());

                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                try {
                    SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);
                    messageHandler.accept(message, sender);

                } catch (InvalidProtocolBufferException e) {
                    System.err.println("[MULTICAST-RECV] Failed to parse message: " + e.getMessage());
                }

            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running) {
                    System.err.println("Multicast receive error: " + e.getMessage());
                }
            }
        }

        cleanup();
    }

    public void stop() {
        running = false;
    }

    private void cleanup() {
        if (joined && socket != null && groupAddress != null) {
            try {
                if (networkInterface != null) {
                    socket.leaveGroup(groupAddress, networkInterface);
                }
            } catch (IOException _) {
            }
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public NetworkInterface getJoinInterface() {
        return networkInterface;
    }

    public InetAddress getLocalAddress() {
        if (networkInterface == null) return null;

        for (InterfaceAddress ia : networkInterface.getInterfaceAddresses()) {
            InetAddress addr = ia.getAddress();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                return addr;
            }
        }
        return null;
    }

    public boolean isJoined() {
        return joined;
    }

    public MulticastSocket getSocketForSend() {
        return socket;
    }
}