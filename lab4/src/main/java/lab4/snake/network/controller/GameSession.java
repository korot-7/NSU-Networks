package lab4.snake.network.controller;

import lab4.snake.game.GameEngine;
import lab4.snake.model.*;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class GameSession {

    private volatile int myPlayerId = -1;
    private volatile String gameName;
    private volatile NodeRole myRole;

    private volatile GameConfig gameConfig;

    private volatile GameEngine gameEngine;

    private volatile GameState currentState;

    private volatile InetSocketAddress masterAddress;
    private volatile InetSocketAddress deputyAddress;
    private volatile int deputyPlayerId = -1;

    private volatile boolean isHost = false;
    private volatile boolean joining = false;
    private volatile long joinStartedAtMs = 0;

    private volatile int lastReceivedStateOrder = -1;
    private volatile InetSocketAddress lastStateSender = null;
    private volatile boolean switchingToDeputy = false;

    private volatile boolean pendingMasterDeathHandoff = false;
    private volatile InetSocketAddress pendingDeputyAddr = null;
    private volatile int pendingDeputyId = -1;

    private volatile int myLastHostPort = -1;

    private final Map<Integer, InetSocketAddress> playerAddresses = new ConcurrentHashMap<>();


    public int getMyPlayerId() { return myPlayerId; }
    public void setMyPlayerId(int id) { this.myPlayerId = id; }

    public String getGameName() { return gameName; }
    public void setGameName(String name) { this.gameName = name; }

    public NodeRole getMyRole() { return myRole; }
    public void setMyRole(NodeRole role) { this.myRole = role; }

    public GameConfig getGameConfig() { return gameConfig; }
    public void setGameConfig(GameConfig config) { this.gameConfig = config; }

    public GameEngine getGameEngine() { return gameEngine; }
    public void setGameEngine(GameEngine engine) { this.gameEngine = engine; }

    public GameState getCurrentState() { return currentState; }
    public void setCurrentState(GameState state) { this.currentState = state; }

    public InetSocketAddress getMasterAddress() { return masterAddress; }
    public void setMasterAddress(InetSocketAddress addr) { this.masterAddress = addr; }

    public InetSocketAddress getDeputyAddress() { return deputyAddress; }
    public void setDeputyAddress(InetSocketAddress addr) { this.deputyAddress = addr; }

    public int getDeputyPlayerId() { return deputyPlayerId; }
    public void setDeputyPlayerId(int id) { this.deputyPlayerId = id; }

    public boolean isHost() { return isHost; }
    public void setHost(boolean host) { this.isHost = host; }

    public boolean isJoining() { return joining; }
    public void setJoining(boolean joining) { this.joining = joining; }

    public long getJoinStartedAtMs() { return joinStartedAtMs; }
    public void setJoinStartedAtMs(long time) { this.joinStartedAtMs = time; }

    public int getLastReceivedStateOrder() { return lastReceivedStateOrder; }
    public void setLastReceivedStateOrder(int order) { this.lastReceivedStateOrder = order; }

    public InetSocketAddress getLastStateSender() { return lastStateSender; }
    public void setLastStateSender(InetSocketAddress sender) { this.lastStateSender = sender; }

    public boolean isSwitchingToDeputy() { return switchingToDeputy; }
    public void setSwitchingToDeputy(boolean switching) { this.switchingToDeputy = switching; }

    public boolean isPendingMasterDeathHandoff() { return pendingMasterDeathHandoff; }
    public void setPendingMasterDeathHandoff(boolean pending) { this.pendingMasterDeathHandoff = pending; }

    public InetSocketAddress getPendingDeputyAddr() { return pendingDeputyAddr; }
    public void setPendingDeputyAddr(InetSocketAddress addr) { this.pendingDeputyAddr = addr; }

    public int getPendingDeputyId() { return pendingDeputyId; }
    public void setPendingDeputyId(int id) { this.pendingDeputyId = id; }

    public int getMyLastHostPort() { return myLastHostPort; }
    public void setMyLastHostPort(int port) { this.myLastHostPort = port; }

    public Map<Integer, InetSocketAddress> getPlayerAddresses() { return playerAddresses; }


    public boolean isInGame() {
        return myRole != null;
    }

    public boolean hasAliveSnake() {
        if (currentState == null || myPlayerId <= 0) return false;
        return currentState.getSnakeByPlayerId(myPlayerId)
                .map(s -> s.getState() == SnakeState.ALIVE)
                .orElse(false);
    }

    public void clearDeputy() {
        this.deputyAddress = null;
        this.deputyPlayerId = -1;
    }

    public void clearPendingHandoff() {
        this.pendingMasterDeathHandoff = false;
        this.pendingDeputyAddr = null;
        this.pendingDeputyId = -1;
    }


    public void reset() {
        myPlayerId = -1;
        gameName = null;
        myRole = null;
        gameConfig = null;
        gameEngine = null;
        currentState = null;
        masterAddress = null;
        deputyAddress = null;
        deputyPlayerId = -1;
        isHost = false;
        joining = false;
        joinStartedAtMs = 0;
        lastReceivedStateOrder = -1;
        lastStateSender = null;
        switchingToDeputy = false;
        pendingMasterDeathHandoff = false;
        pendingDeputyAddr = null;
        pendingDeputyId = -1;
        playerAddresses.clear();
    }
}