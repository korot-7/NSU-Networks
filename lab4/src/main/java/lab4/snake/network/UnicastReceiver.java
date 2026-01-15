package lab4.snake.network;

import com.google.protobuf.InvalidProtocolBufferException;
import lab4.protobuf.SnakesProto;
import lab4.snake.util.Config;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.function.BiConsumer;

public class UnicastReceiver implements Runnable {
    private final UDPSocket socket;
    private final BiConsumer<SnakesProto.GameMessage, InetSocketAddress> messageHandler;
    private volatile boolean running = true;

    public UnicastReceiver(UDPSocket socket,
                           BiConsumer<SnakesProto.GameMessage, InetSocketAddress> messageHandler) throws SocketException {
        this.socket = socket;
        this.messageHandler = messageHandler;
        socket.setSoTimeout(Config.SOCKET_TIMEOUT);
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Config.UDP_BUFFER_SIZE];

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.getSocket().receive(packet);

                InetSocketAddress sender = new InetSocketAddress(
                        packet.getAddress(), packet.getPort());

                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());

                try {
                    SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);
                    messageHandler.accept(message, sender);
                } catch (InvalidProtocolBufferException e) {
                    System.err.println("Failed to parse unicast message: " + e.getMessage());
                }

            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running && !socket.isClosed()) {
                    System.err.println("Unicast receive error: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
    }
}
