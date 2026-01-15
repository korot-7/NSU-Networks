package lab4.snake.network.controller;

import lab4.snake.game.GameEngine;
import lab4.snake.model.GamePlayer;
import lab4.snake.model.NodeRole;
import lab4.snake.network.NodeTracker;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class PlayerRegistry {

    private final GameSession session;
    private final NodeTracker nodeTracker;

    private final Map<InetSocketAddress, Set<Long>> processedJoinSeqs = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, Long> lastJoinTime = new ConcurrentHashMap<>();

    private final Map<InetSocketAddress, Long> playerJoinTime = new ConcurrentHashMap<>();

    private static final long JOIN_COOLDOWN_MS = 500;
    private static final long NEW_PLAYER_GRACE_PERIOD_MS = 10_000;

    public PlayerRegistry(GameSession session, NodeTracker nodeTracker) {
        this.session = session;
        this.nodeTracker = nodeTracker;
    }


    public int findPlayerIdByAddress(InetSocketAddress addr) {
        for (var entry : session.getPlayerAddresses().entrySet()) {
            if (entry.getValue().equals(addr)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public int findActivePlayerIdByAddress(InetSocketAddress addr) {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return -1;

        for (var entry : session.getPlayerAddresses().entrySet()) {
            if (entry.getValue().equals(addr)) {
                int playerId = entry.getKey();
                Optional<GamePlayer> player = engine.getState().getPlayerById(playerId);
                if (player.isPresent() && player.get().getRole() != NodeRole.VIEWER) {
                    return playerId;
                }
            }
        }
        return -1;
    }

    public int findViewerIdByAddress(InetSocketAddress addr) {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return -1;

        for (var entry : session.getPlayerAddresses().entrySet()) {
            if (entry.getValue().equals(addr)) {
                int playerId = entry.getKey();
                Optional<GamePlayer> player = engine.getState().getPlayerById(playerId);
                if (player.isPresent() && player.get().getRole() == NodeRole.VIEWER) {
                    return playerId;
                }
            }
        }

        for (GamePlayer player : engine.getState().getPlayers()) {
            if (player.getRole() == NodeRole.VIEWER &&
                    player.getAddress() != null &&
                    player.getAddress().equals(addr)) {
                return player.getId();
            }
        }

        return -1;
    }


    public boolean isDuplicateJoinSeq(InetSocketAddress addr, long msgSeq) {
        Set<Long> seqs = processedJoinSeqs.get(addr);
        return seqs != null && seqs.contains(msgSeq);
    }

    public void recordJoinSeq(InetSocketAddress addr, long msgSeq) {
        Set<Long> seqs = processedJoinSeqs.computeIfAbsent(addr, k -> ConcurrentHashMap.newKeySet());
        seqs.add(msgSeq);
    }

    public void removeJoinSeq(InetSocketAddress addr, long msgSeq) {
        Set<Long> seqs = processedJoinSeqs.get(addr);
        if (seqs != null) {
            seqs.remove(msgSeq);
        }
    }

    public boolean isJoinCooldownActive(InetSocketAddress addr) {
        Long lastJoin = lastJoinTime.get(addr);
        if (lastJoin == null) return false;
        return System.currentTimeMillis() - lastJoin < JOIN_COOLDOWN_MS;
    }

    public void recordJoinTime(InetSocketAddress addr) {
        lastJoinTime.put(addr, System.currentTimeMillis());
    }


    public void registerNewPlayer(InetSocketAddress addr) {
        playerJoinTime.put(addr, System.currentTimeMillis());
        nodeTracker.addNode(addr);
        nodeTracker.updateLastSeen(addr);
    }

    public boolean isInGracePeriod(InetSocketAddress addr) {
        Long joinTime = playerJoinTime.get(addr);
        if (joinTime == null) return false;
        return System.currentTimeMillis() - joinTime < NEW_PLAYER_GRACE_PERIOD_MS;
    }

    public void grantGracePeriod(InetSocketAddress addr) {
        playerJoinTime.put(addr, System.currentTimeMillis());
        nodeTracker.updateLastSeen(addr);
    }


    public void removePlayer(InetSocketAddress addr) {
        nodeTracker.removeNode(addr);
        lastJoinTime.remove(addr);
        processedJoinSeqs.remove(addr);
        playerJoinTime.remove(addr);
    }


    public void clear() {
        processedJoinSeqs.clear();
        lastJoinTime.clear();
        playerJoinTime.clear();
    }


    public List<InetSocketAddress> findTimedOutNodes() {
        List<InetSocketAddress> timedOut = new ArrayList<>();

        for (InetSocketAddress addr : new HashSet<>(nodeTracker.getAllNodes())) {
            if (isInGracePeriod(addr)) {
                continue;
            }

            Long joinTime = playerJoinTime.get(addr);
            if (joinTime == null) {
                int playerId = findPlayerIdByAddress(addr);
                if (playerId == -1) {
                    nodeTracker.removeNode(addr);
                    continue;
                }
                grantGracePeriod(addr);
                continue;
            }

            if (nodeTracker.isTimedOut(addr)) {
                timedOut.add(addr);
            }
        }

        return timedOut;
    }
}