package lab4.snake.network;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeTracker {
    private final Map<InetSocketAddress, Long> lastSeenTime;
    private volatile long nodeTimeoutMs;

    public NodeTracker(long nodeTimeoutMs) {
        this.lastSeenTime = new ConcurrentHashMap<>();
        this.nodeTimeoutMs = nodeTimeoutMs;
    }

    public void updateLastSeen(InetSocketAddress address) {
        lastSeenTime.put(address, System.currentTimeMillis());
    }

    public void setTimeoutMs(long timeoutMs) {
        this.nodeTimeoutMs = timeoutMs;
    }

    public void addNode(InetSocketAddress address) {
        updateLastSeen(address);
    }

    public void removeNode(InetSocketAddress address) {
        lastSeenTime.remove(address);
    }

    public boolean isTimedOut(InetSocketAddress address) {
        Long lastSeen = lastSeenTime.get(address);
        if (lastSeen == null) {
            return true;
        }
        return System.currentTimeMillis() - lastSeen > nodeTimeoutMs;
    }

    public Set<InetSocketAddress> getAllNodes() {
        return new HashSet<>(lastSeenTime.keySet());
    }

    public void clear() {
        lastSeenTime.clear();
    }
}
