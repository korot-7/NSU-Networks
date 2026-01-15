package lab4.snake.network;

import lab4.protobuf.SnakesProto;

import java.net.InetSocketAddress;

public class PendingMessage {
    private final SnakesProto.GameMessage message;
    private InetSocketAddress target;
    private long sentTime;

    public PendingMessage(SnakesProto.GameMessage message, InetSocketAddress target) {
        this.message = message;
        this.target = target;
        this.sentTime = System.currentTimeMillis();
    }

    public SnakesProto.GameMessage getMessage() {
        return message;
    }

    public long getMsgSeq() {
        return message.getMsgSeq();
    }

    public InetSocketAddress getTarget() {
        return target;
    }

    public void setTarget(InetSocketAddress target) {
        this.target = target;
    }

    public void updateSentTime() {
        this.sentTime = System.currentTimeMillis();
    }

    public boolean isExpired(long retransmitIntervalMs) {
        return System.currentTimeMillis() - sentTime > retransmitIntervalMs;
    }
}
