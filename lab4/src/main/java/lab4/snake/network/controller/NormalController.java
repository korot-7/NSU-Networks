package lab4.snake.network.controller;

import lab4.protobuf.SnakesProto;
import lab4.snake.game.GameEngine;
import lab4.snake.model.*;
import lab4.snake.network.MessageHandler;
import lab4.snake.network.MessageSender;
import lab4.snake.network.NodeTracker;
import lab4.snake.util.ProtoConverter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class NormalController {

    private final NetworkManager networkManager;
    private final GameSession session;
    private final MessageSender messageSender;
    private final NodeTracker nodeTracker;
    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> maintenanceTask;
    private ScheduledFuture<?> joinWatchdogTask;

    private static final long JOIN_GRACE_MS = 3000;
    private static final int STATE_ORDER_JUMP_ON_MASTER_CHANGE = 10;
    private static final int JOIN_TIME = 15_000;

    public NormalController(NetworkManager networkManager,
                            GameSession session,
                            MessageSender messageSender,
                            NodeTracker nodeTracker,
                            ScheduledExecutorService executor) {
        this.networkManager = networkManager;
        this.session = session;
        this.messageSender = messageSender;
        this.nodeTracker = nodeTracker;
        this.executor = executor;
    }


    public void joinGame(GameAnnouncement game, String playerName, boolean asViewer) {
        System.out.println("=== JOIN GAME: " + game.getGameName() + " ===");
        System.out.println("Master: " + game.getMasterAddress());

        InetSocketAddress targetMaster = game.getMasterAddress();
        if (isOwnAddress(targetMaster)) {
            System.out.println("Cannot join own game!");
            networkManager.notifyError("Нельзя присоединиться к своей игре");
            return;
        }

        session.reset();
        networkManager.getPlayerRegistry().clear();
        messageSender.clearPending();

        session.setGameConfig(game.getConfig());
        session.setGameName(game.getGameName());
        session.setMasterAddress(game.getMasterAddress());
        session.setMyRole(asViewer ? NodeRole.VIEWER : NodeRole.NORMAL);
        session.setHost(false);
        session.setMyPlayerId(-1);
        session.setLastReceivedStateOrder(-1);
        session.clearDeputy();
        session.setCurrentState(null);
        session.setLastStateSender(null);
        session.setJoining(true);
        session.setJoinStartedAtMs(System.currentTimeMillis());
        session.setGameEngine(null);

        if (session.getMasterAddress() != null) {
            nodeTracker.addNode(session.getMasterAddress());
            nodeTracker.updateLastSeen(session.getMasterAddress());
        }

        try {
            NodeRole requestedRole = asViewer ? NodeRole.VIEWER : NodeRole.NORMAL;
            SnakesProto.GameMessage.Builder msg = ProtoConverter.createJoinMsg(
                    playerName, game.getGameName(), requestedRole, PlayerType.HUMAN);
            long msgSeq = messageSender.sendWithAck(msg, session.getMasterAddress());
            System.out.println("JoinMsg sent, seq=" + msgSeq);
        } catch (IOException e) {
            System.err.println("Failed to send JoinMsg: " + e.getMessage());
            networkManager.notifyError("Ошибка подключения: " + e.getMessage());
            return;
        }

        startJoinWatchdog();
        startMaintenance();
    }

    private void startJoinWatchdog() {
        cancelTask(joinWatchdogTask);

        joinWatchdogTask = executor.scheduleAtFixedRate(() -> {
            if (session.getCurrentState() != null || session.getMyPlayerId() > 0) {
                cancelTask(joinWatchdogTask);
                joinWatchdogTask = null;
                return;
            }

            long elapsed = System.currentTimeMillis() - session.getJoinStartedAtMs();
            if (elapsed > JOIN_TIME) {
                System.out.println("Join timeout after 15 seconds");
                session.setJoining(false);
                cancelTask(joinWatchdogTask);
                joinWatchdogTask = null;
                networkManager.notifyError("Не удалось подключиться к игре");
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);
    }

    private void startMaintenance() {
        cancelTask(maintenanceTask);

        long maintenanceInterval = Math.max(session.getGameConfig().getPingDelayMs(), 200);
        maintenanceTask = executor.scheduleAtFixedRate(
                this::maintenance, maintenanceInterval,
                maintenanceInterval, TimeUnit.MILLISECONDS);
    }

    public void cancelJoinWatchdog() {
        cancelTask(joinWatchdogTask);
        joinWatchdogTask = null;
    }

    public void stopTasks() {
        cancelTask(maintenanceTask);
        cancelTask(joinWatchdogTask);
        maintenanceTask = null;
        joinWatchdogTask = null;
    }

    private void cancelTask(Future<?> task) {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }

    private boolean isOwnAddress(InetSocketAddress addr) {
        if (addr == null) return false;
        int localPort = networkManager.getLocalPort();
        return addr.getPort() == localPort || addr.getPort() == session.getMyLastHostPort();
    }


    public void handleState(MessageHandler.StateEvent event) {
        networkManager.sendAck(event.msgSeq(), event.sender());

        if (session.isHost()) {
            return;
        }

        if (session.getMasterAddress() != null && !session.getMasterAddress().equals(event.sender())) {
            int incomingOrder = event.state().getStateOrder();
            if (incomingOrder <= session.getLastReceivedStateOrder()) {
                System.out.println("[STATE] Ignoring from " + event.sender() +
                        " (order " + incomingOrder + " <= " + session.getLastReceivedStateOrder() + ")");
                return;
            }
            System.out.println("[STATE] Switching to new master: " + event.sender());
        }

        boolean masterChanged = (session.getLastStateSender() == null) ||
                !session.getLastStateSender().equals(event.sender());

        if (masterChanged) {
            System.out.println("[STATE] Master changed: " + session.getLastStateSender() + " -> " + event.sender());
            session.setSwitchingToDeputy(false);
            session.setMasterAddress(event.sender());
            session.setLastStateSender(event.sender());
            nodeTracker.updateLastSeen(session.getMasterAddress());
            nodeTracker.addNode(session.getMasterAddress());
            session.clearDeputy();
        }

        int incomingOrder = event.state().getStateOrder();
        if (incomingOrder <= session.getLastReceivedStateOrder()) {
            return;
        }
        session.setLastReceivedStateOrder(incomingOrder);

        GameConfig config = session.getGameConfig();
        if (config == null) {
            return;
        }

        GameState state = ProtoConverter.fromProto(event.state(), config, event.sender());
        session.setCurrentState(state);

        state.getMaster().ifPresent(master -> {
            if (master.getAddress() == null) {
                master.setAddress(event.sender());
            }
            session.getPlayerAddresses().put(master.getId(), event.sender());
        });

        updatePlayerAddresses(state.getPlayers());

        if (session.getMyPlayerId() > 0) {
            state.getPlayerById(session.getMyPlayerId()).ifPresent(player -> {
                NodeRole stateRole = player.getRole();
                if (session.getMyRole() != stateRole) {
                    System.out.println("[STATE] My role updated: " + session.getMyRole() + " -> " + stateRole);
                    session.setMyRole(stateRole);
                    networkManager.notifyRoleChange();
                }
            });
        }

        session.setJoining(false);
        cancelJoinWatchdog();

        networkManager.notifyStateUpdate();
    }

    public void handleRoleChange(MessageHandler.RoleChangeEvent event) {
        System.out.println("=== RoleChange: sender=" + event.senderRole() +
                ", receiver=" + event.receiverRole() + " ===");

        if (event.receiverRole() != null && event.receiverId() == session.getMyPlayerId()) {
            NodeRole newRole = event.receiverRole();

            if (newRole == NodeRole.DEPUTY && session.getMyRole() != NodeRole.DEPUTY) {
                System.out.println("I'm becoming DEPUTY");
                session.setMyRole(NodeRole.DEPUTY);
                if (session.getCurrentState() != null) {
                    session.getCurrentState().getPlayerById(session.getMyPlayerId())
                            .ifPresent(p -> p.setRole(NodeRole.DEPUTY));
                }
                networkManager.notifyRoleChange();
                networkManager.notifyStateUpdate();

            } else if (newRole == NodeRole.MASTER && session.getMyRole() != NodeRole.MASTER) {
                System.out.println("I'm becoming MASTER!");
                NodeRole senderRole = (event.senderRole() != null) ? event.senderRole() : NodeRole.MASTER;
                becomeMaster(event.senderId(), event.sender(), senderRole);
                return;

            } else if (newRole == NodeRole.VIEWER && session.getMyRole() != NodeRole.VIEWER) {
                System.out.println("I'm becoming VIEWER");
                session.setMyRole(NodeRole.VIEWER);
                if (session.getCurrentState() != null) {
                    session.getCurrentState().getPlayerById(session.getMyPlayerId())
                            .ifPresent(p -> p.setRole(NodeRole.VIEWER));
                }
                networkManager.notifyRoleChange();
                networkManager.notifyStateUpdate();
            }
        }

        if (event.senderRole() == NodeRole.MASTER && event.senderId() != session.getMyPlayerId()) {
            boolean masterActuallyChanged = (session.getMasterAddress() == null) ||
                    !session.getMasterAddress().equals(event.sender());
            if (masterActuallyChanged) {
                System.out.println("New MASTER: " + event.senderId() + " at " + event.sender());
                session.setMasterAddress(event.sender());
                session.setLastReceivedStateOrder(-1);
                session.setLastStateSender(null);
                session.clearDeputy();
            }
            nodeTracker.updateLastSeen(session.getMasterAddress());
            session.getPlayerAddresses().put(event.senderId(), event.sender());
        }
    }

    public void sendSteer(Direction direction) {
        if (session.getMasterAddress() != null) {
            try {
                SnakesProto.GameMessage.Builder msg = ProtoConverter.createSteerMsg(direction);
                msg.setSenderId(session.getMyPlayerId());
                messageSender.sendWithAck(msg, session.getMasterAddress());
            } catch (IOException e) {
                System.err.println("Failed to send steer: " + e.getMessage());
            }
        }
    }

    public void leaveGame() {
        if (session.getMasterAddress() != null && session.getMyPlayerId() > 0) {
            try {
                SnakesProto.GameMessage.Builder msg = ProtoConverter.createRoleChangeMsg(
                        NodeRole.VIEWER, null, session.getMyPlayerId(), -1);
                messageSender.sendWithAck(msg, session.getMasterAddress());
            } catch (IOException e) {
                System.err.println("Failed to send leave: " + e.getMessage());
            }
        }
        stopTasks();
    }


    private void becomeMaster(int oldMasterId, InetSocketAddress oldMasterAddr, NodeRole oldMasterSenderRole) {
        System.out.println("=== BECOMING MASTER ===");
        System.out.println("Old master ID: " + oldMasterId + ", addr: " + oldMasterAddr);

        if (session.getCurrentState() == null) {
            System.err.println("Cannot become MASTER - no state!");
            networkManager.notifyGameOver();
            return;
        }

        stopTasks();

        session.setMyRole(NodeRole.MASTER);
        session.setHost(true);
        session.setMasterAddress(null);
        session.setSwitchingToDeputy(false);
        session.setLastStateSender(null);
        session.setMyLastHostPort(networkManager.getLocalPort());

        GameState stateCopy = session.getCurrentState().copy();
        for (int i = 0; i < STATE_ORDER_JUMP_ON_MASTER_CHANGE; i++) {
            stateCopy.incrementStateOrder();
        }

        GameEngine engine = new GameEngine(stateCopy);
        session.setGameEngine(engine);

        engine.getState().getPlayerById(session.getMyPlayerId())
                .ifPresent(p -> p.setRole(NodeRole.MASTER));

        if (oldMasterId > 0 && oldMasterId != session.getMyPlayerId()) {
            System.out.println("Processing old master: " + oldMasterId);

            if (oldMasterSenderRole == NodeRole.VIEWER) {
                engine.getState().getPlayerById(oldMasterId)
                        .ifPresent(p -> p.setRole(NodeRole.VIEWER));
                engine.getState().getSnakeByPlayerId(oldMasterId)
                        .ifPresent(s -> s.setState(SnakeState.ZOMBIE));
                if (oldMasterAddr != null) {
                    session.getPlayerAddresses().put(oldMasterId, oldMasterAddr);
                }
            } else {
                engine.getState().getSnakeByPlayerId(oldMasterId)
                        .ifPresent(s -> s.setState(SnakeState.ZOMBIE));
                engine.getState().getPlayers().removeIf(p -> p.getId() == oldMasterId);
                session.getPlayerAddresses().remove(oldMasterId);
            }
        }

        for (GamePlayer p : engine.getState().getPlayers()) {
            if (p.getId() != session.getMyPlayerId() && p.getRole() == NodeRole.MASTER) {
                p.setRole(NodeRole.VIEWER);
            }
        }

        networkManager.getPlayerRegistry().clear();

        for (GamePlayer p : engine.getState().getPlayers()) {
            if (p.getId() == session.getMyPlayerId()) continue;

            InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
            if (addr == null) {
                addr = p.getAddress();
            }

            if (addr != null && addr.equals(oldMasterAddr) && p.getId() != oldMasterId) {
                addr = null;
            }

            if (addr != null) {
                session.getPlayerAddresses().put(p.getId(), addr);
                p.setAddress(addr);
                networkManager.getPlayerRegistry().registerNewPlayer(addr);
            }
        }

        session.clearDeputy();
        session.setCurrentState(engine.getState());


        networkManager.getMasterController().startAsNewMaster();

        notifyAllAboutNewMaster();

        executor.schedule(() -> {
            if (session.isHost() && session.getDeputyAddress() == null) {
                selectNewDeputy();
            }
        }, 500, TimeUnit.MILLISECONDS);

        networkManager.notifyRoleChange();
        networkManager.notifyStateUpdate();

        System.out.println("=== NOW MASTER ===");
    }

    private void notifyAllAboutNewMaster() {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        List<GamePlayer> snapshot = new ArrayList<>(engine.getState().getPlayers());

        for (GamePlayer p : snapshot) {
            if (p.getId() == session.getMyPlayerId()) continue;

            InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
            if (addr == null) addr = p.getAddress();
            if (addr == null) continue;

            NodeRole receiverRole = (p.getId() == session.getDeputyPlayerId()) ? NodeRole.DEPUTY : null;

            try {
                SnakesProto.GameMessage.Builder msg = ProtoConverter.createRoleChangeMsg(
                        NodeRole.MASTER, receiverRole, session.getMyPlayerId(), p.getId());
                messageSender.sendWithAck(msg, addr);
            } catch (IOException e) {
                System.err.println("Failed to notify player " + p.getId());
            }
        }
    }

    private void selectNewDeputy() {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        for (GamePlayer p : engine.getState().getPlayers()) {
            if (p.getRole() == NodeRole.NORMAL && p.getId() != session.getMyPlayerId()) {
                InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
                if (addr != null && !nodeTracker.isTimedOut(addr)) {
                    session.setDeputyPlayerId(p.getId());
                    session.setDeputyAddress(addr);

                    engine.setPlayerRole(p.getId(), NodeRole.DEPUTY);
                    engine.getState().getPlayerById(p.getId())
                            .ifPresent(player -> player.setRole(NodeRole.DEPUTY));
                    session.setCurrentState(engine.getState());

                    sendRoleChange(null, NodeRole.DEPUTY, p.getId(), addr);
                    networkManager.notifyStateUpdate();
                    return;
                }
            }
        }
        System.out.println("No DEPUTY candidate found");
    }


    private void maintenance() {
        GameConfig config = session.getGameConfig();
        if (config == null) return;

        long pingInterval = Math.max(config.getPingDelayMs(), 100);

        messageSender.retransmitUnconfirmed(pingInterval);

        checkMasterTimeout();

        sendPingsIfNeeded(pingInterval);
    }

    private void checkMasterTimeout() {
        InetSocketAddress master = session.getMasterAddress();
        if (master == null) return;

        if (session.isJoining()) {
            long dt = System.currentTimeMillis() - session.getJoinStartedAtMs();
            nodeTracker.updateLastSeen(master);

            if (session.getCurrentState() == null || dt < JOIN_GRACE_MS) {
                return;
            }
        }

        if (nodeTracker.isTimedOut(master)) {
            handleMasterTimeout();
        }
    }

    private void handleMasterTimeout() {
        System.out.println("MASTER timeout, my role=" + session.getMyRole());

        if (session.isSwitchingToDeputy()) {
            InetSocketAddress master = session.getMasterAddress();
            if (master != null && nodeTracker.isTimedOut(master)) {
                networkManager.notifyGameOver();
            }
            return;
        }

        if (session.getMyRole() == NodeRole.DEPUTY) {
            int oldMasterId = findMasterIdFromState();
            becomeMaster(oldMasterId, session.getMasterAddress(), NodeRole.MASTER);

        } else if (session.getMyRole() == NodeRole.NORMAL || session.getMyRole() == NodeRole.VIEWER) {
            if (session.getDeputyAddress() != null) {
                System.out.println("Switching to DEPUTY: " + session.getDeputyAddress());
                InetSocketAddress master = session.getMasterAddress();
                if (master != null) {
                    messageSender.retargetUnconfirmed(master, session.getDeputyAddress());
                }
                session.setMasterAddress(session.getDeputyAddress());
                session.setLastStateSender(null);
                session.setLastReceivedStateOrder(-1);
                session.clearDeputy();
                session.setSwitchingToDeputy(true);
                nodeTracker.updateLastSeen(session.getMasterAddress());
            } else {
                System.out.println("No DEPUTY known");
                networkManager.notifyGameOver();
            }
        }
    }

    private int findMasterIdFromState() {
        GameState state = session.getCurrentState();
        if (state == null) return -1;
        return state.getPlayers().stream()
                .filter(p -> p.getRole() == NodeRole.MASTER)
                .map(GamePlayer::getId)
                .findFirst()
                .orElse(-1);
    }

    private void sendPingsIfNeeded(long pingInterval) {
        InetSocketAddress master = session.getMasterAddress();
        if (master != null && messageSender.needsPing(master, pingInterval)) {
            sendPing(master);
        }
    }

    private void sendPing(InetSocketAddress target) {
        try {
            SnakesProto.GameMessage.Builder msg = ProtoConverter.createPingMsg();
            if (session.getMyPlayerId() > 0) msg.setSenderId(session.getMyPlayerId());
            messageSender.sendWithAck(msg, target);
        } catch (IOException e) {
            System.err.println("Failed to send ping: " + e.getMessage());
        }
    }


    private void updatePlayerAddresses(List<GamePlayer> players) {
        for (GamePlayer p : players) {
            if (p.getId() == session.getMyPlayerId()) continue;

            InetSocketAddress newAddr = p.getAddress();
            if (newAddr != null) {
                session.getPlayerAddresses().put(p.getId(), newAddr);
            }

            if (p.getRole() == NodeRole.DEPUTY) {
                session.setDeputyPlayerId(p.getId());
                if (p.getAddress() != null) {
                    session.setDeputyAddress(p.getAddress());
                } else if (session.getPlayerAddresses().containsKey(p.getId())) {
                    session.setDeputyAddress(session.getPlayerAddresses().get(p.getId()));
                }
            }
        }
    }

    private void sendRoleChange(NodeRole senderRole, NodeRole receiverRole,
                                int receiverId, InetSocketAddress target) {
        try {
            SnakesProto.GameMessage.Builder msg = ProtoConverter.createRoleChangeMsg(
                    senderRole, receiverRole, session.getMyPlayerId(), receiverId);
            messageSender.sendWithAck(msg, target);
        } catch (IOException e) {
            System.err.println("Failed to send role change");
        }
    }
}