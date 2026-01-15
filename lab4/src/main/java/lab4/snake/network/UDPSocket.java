package lab4.snake.network;

import java.io.IOException;
import java.net.*;

public class UDPSocket implements AutoCloseable {
    private final DatagramSocket socket;

    public UDPSocket() throws SocketException {
        this.socket = new DatagramSocket();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public void send(byte[] data, InetSocketAddress target) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length,
                target.getAddress(), target.getPort());
        socket.send(packet);
    }

    public void setSoTimeout(int timeoutMs) throws SocketException {
        socket.setSoTimeout(timeoutMs);
    }

    @Override
    public void close() {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }
}
