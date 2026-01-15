package lab4.snake.network;

import lab4.protobuf.SnakesProto;
import lab4.snake.util.Config;

import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MessageSender {
    private final UDPSocket socket;
    private final AtomicLong msgSeqCounter;
    private final Map<Long, PendingMessage> unconfirmedMessages;
    private final Map<InetSocketAddress, Long> lastSentTime;
    private MulticastSocket multicastSocket;

    public MessageSender(UDPSocket socket) {
        this.socket = socket;
        this.msgSeqCounter = new AtomicLong(0);
        this.unconfirmedMessages = new ConcurrentHashMap<>();
        this.lastSentTime = new ConcurrentHashMap<>();
    }

    public void setMulticastSocket(MulticastSocket socket) {
        this.multicastSocket = socket;
    }

    public long nextMsgSeq() {
        return msgSeqCounter.incrementAndGet();
    }

    public long sendWithAck(SnakesProto.GameMessage.Builder messageBuilder,
                            InetSocketAddress target) throws IOException {
        long seq = nextMsgSeq();
        messageBuilder.setMsgSeq(seq);
        SnakesProto.GameMessage message = messageBuilder.build();

        unconfirmedMessages.put(seq, new PendingMessage(message, target));

        sendRaw(message, target);
        return seq;
    }

    public void sendNoAck(SnakesProto.GameMessage message, InetSocketAddress target)
            throws IOException {
        sendRaw(message, target);
    }


    public void sendMulticast(SnakesProto.GameMessage message) throws IOException {
        byte[] data = message.toByteArray();

        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        Config.MULTICAST_ADDRESS.getAddress(),
                        Config.MULTICAST_ADDRESS.getPort());
                multicastSocket.send(packet);
                return;
            } catch (IOException e) {
                System.err.println("Multicast socket send failed, falling back to unicast: " + e.getMessage());
            }
        }

        socket.send(data, Config.MULTICAST_ADDRESS);
    }


    private void sendRaw(SnakesProto.GameMessage message, InetSocketAddress target)
            throws IOException {
        byte[] data = message.toByteArray();
        socket.send(data, target);
        lastSentTime.put(target, System.currentTimeMillis());
    }

    public void onAckReceived(long msgSeq) {
        unconfirmedMessages.remove(msgSeq);
    }

    public void retransmitUnconfirmed(long retransmitIntervalMs) {
        for (PendingMessage pending : unconfirmedMessages.values()) {
            if (pending.isExpired(retransmitIntervalMs)) {
                try {
                    sendRaw(pending.getMessage(), pending.getTarget());
                    pending.updateSentTime();
                } catch (IOException e) {
                    System.err.println("Retransmit failed: " + e.getMessage());
                }
            }
        }
    }

    public void retargetUnconfirmed(InetSocketAddress oldTarget, InetSocketAddress newTarget) {
        for (PendingMessage pending : unconfirmedMessages.values()) {
            if (pending.getTarget().equals(oldTarget)) {
                pending.setTarget(newTarget);
            }
        }
    }

    public boolean needsPing(InetSocketAddress target, long pingIntervalMs) {
        Long lastSent = lastSentTime.get(target);
        if (lastSent == null) {
            return true;
        }
        return System.currentTimeMillis() - lastSent > pingIntervalMs;
    }

    public void removeTarget(InetSocketAddress target) {
        lastSentTime.remove(target);
        unconfirmedMessages.entrySet().removeIf(
                entry -> entry.getValue().getTarget().equals(target));
    }

    public void clearPending() {
        unconfirmedMessages.clear();
    }
}