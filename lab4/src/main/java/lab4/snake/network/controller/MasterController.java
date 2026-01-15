package lab4.snake.network.controller;

import lab4.protobuf.SnakesProto;
import lab4.snake.game.GameEngine;
import lab4.snake.model.*;
import lab4.snake.network.MessageHandler;
import lab4.snake.network.MessageSender;
import lab4.snake.network.NodeTracker;
import lab4.snake.util.Config;
import lab4.snake.util.ProtoConverter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class MasterController {

    private final NetworkManager networkManager;
    private final GameSession session;
    private final PlayerRegistry playerRegistry;
    private final MessageSender messageSender;
    private final NodeTracker nodeTracker;
    private final ScheduledExecutorService executor;

    private final Object hostLock = new Object();

    private ScheduledFuture<?> gameLoopTask;
    private ScheduledFuture<?> announcementTask;
    private ScheduledFuture<?> maintenanceTask;

    public MasterController(NetworkManager networkManager,
                            GameSession session,
                            PlayerRegistry playerRegistry,
                            MessageSender messageSender,
                            NodeTracker nodeTracker,
                            ScheduledExecutorService executor) {
        this.networkManager = networkManager;
        this.session = session;
        this.playerRegistry = playerRegistry;
        this.messageSender = messageSender;
        this.nodeTracker = nodeTracker;
        this.executor = executor;
    }


    public void createGame(GameConfig config, String playerName, String gameName) {
        synchronized (hostLock) {
            session.setGameConfig(config);
            session.setGameName(gameName);
            session.setMyRole(NodeRole.MASTER);
            session.setHost(true);
            session.clearDeputy();
            session.setLastReceivedStateOrder(-1);
            session.setLastStateSender(null);
            session.setMyLastHostPort(networkManager.getLocalPort());
            session.setJoining(false);

            GameEngine engine = new GameEngine(config);
            engine.setOnPlayerDeath(this::onPlayerDeath);
            session.setGameEngine(engine);

            int myId = engine.getNextPlayerId();
            session.setMyPlayerId(myId);

            GamePlayer me = GamePlayer.newPlayer(myId, playerName, null, NodeRole.MASTER, PlayerType.HUMAN);
            engine.addPlayer(me);

            session.setCurrentState(engine.getState());

            playerRegistry.clear();

            startHostLoop();

            System.out.println("Game created: " + gameName + " on port " + session.getMyLastHostPort());
            networkManager.notifyStateUpdate();
            networkManager.notifyRoleChange();
        }
    }


    public void startAsNewMaster() {
        synchronized (hostLock) {
            if (!session.isHost()) {
                System.err.println("[startAsNewMaster] session.isHost() == false, aborting");
                return;
            }

            GameEngine engine = session.getGameEngine();
            if (engine == null) {
                System.err.println("[startAsNewMaster] GameEngine is null, aborting");
                return;
            }

            engine.setOnPlayerDeath(this::onPlayerDeath);

            startHostLoop();

            System.out.println("[startAsNewMaster] Host loop started successfully");
        }
    }


    private void startHostLoop() {
        stopTasks();

        GameConfig config = session.getGameConfig();
        if (config == null) {
            System.err.println("[startHostLoop] GameConfig is null!");
            return;
        }

        gameLoopTask = executor.scheduleAtFixedRate(
                this::gameTick, config.stateDelayMs(),
                config.stateDelayMs(), TimeUnit.MILLISECONDS);

        announcementTask = executor.scheduleAtFixedRate(
                this::sendAnnouncement, 0,
                Config.ANNOUNCEMENT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        long maintenanceInterval = Math.max(config.getPingDelayMs(), 200);
        maintenanceTask = executor.scheduleAtFixedRate(
                this::maintenance, maintenanceInterval,
                maintenanceInterval, TimeUnit.MILLISECONDS);

        System.out.println("[startHostLoop] Started: gameLoop=" + config.stateDelayMs() +
                "ms, maintenance=" + maintenanceInterval + "ms");
    }

    public void stopTasks() {
        synchronized (hostLock) {
            cancelTask(gameLoopTask);
            cancelTask(announcementTask);
            cancelTask(maintenanceTask);
            gameLoopTask = null;
            announcementTask = null;
            maintenanceTask = null;
        }
    }

    private void cancelTask(Future<?> task) {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }

    private void gameTick() {
        synchronized (hostLock) {
            if (!session.isHost()) return;

            GameEngine engine = session.getGameEngine();
            if (engine == null) return;

            try {
                session.setCurrentState(engine.tick());

                broadcastState();
                networkManager.notifyStateUpdate();

                if (session.isPendingMasterDeathHandoff()) {
                    session.setPendingMasterDeathHandoff(false);

                    InetSocketAddress depAddr = session.getPendingDeputyAddr();
                    int depId = session.getPendingDeputyId();

                    session.clearPendingHandoff();

                    executor.execute(() -> handoffMasterToDeputy(depId, depAddr));
                }

            } catch (Exception e) {
                System.err.println("Tick error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private void broadcastState() {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        GameState state = engine.getState();
        List<GamePlayer> players = new ArrayList<>(state.getPlayers());

        for (GamePlayer player : players) {
            if (player.getId() == session.getMyPlayerId()) continue;

            InetSocketAddress addr = session.getPlayerAddresses().get(player.getId());
            if (addr == null) addr = player.getAddress();
            if (addr == null) continue;

            try {
                SnakesProto.GameMessage.Builder msg =
                        ProtoConverter.createStateMsg(state, session.getMyPlayerId());
                messageSender.sendWithAck(msg, addr);
            } catch (IOException e) {
                System.err.println("Failed to send state to " + player.getId());
            }
        }
    }

    private void sendAnnouncement() {
        synchronized (hostLock) {
            if (!session.isHost()) return;

            GameEngine engine = session.getGameEngine();
            if (engine == null) return;

            try {
                List<GamePlayer> playersWithMasterAddress = new ArrayList<>();
                int localPort = networkManager.getLocalPort();

                InetAddress localAddress = networkManager.getLocalAddress();
                if (localAddress == null) {
                    localAddress = InetAddress.getLocalHost();
                }

                InetSocketAddress masterAddr = new InetSocketAddress(localAddress, localPort);

                for (GamePlayer p : engine.getState().getPlayers()) {
                    if (p.getId() == session.getMyPlayerId() && p.getRole() == NodeRole.MASTER) {
                        GamePlayer masterWithAddr = new GamePlayer(
                                p.getId(), p.getName(), masterAddr,
                                p.getRole(), p.getType(), p.getScore());
                        playersWithMasterAddress.add(masterWithAddr);
                    } else {
                        playersWithMasterAddress.add(p);
                    }
                }

                GameAnnouncement ann = new GameAnnouncement(
                        session.getGameName(), session.getGameConfig(),
                        playersWithMasterAddress, engine.canJoin(), null);

                SnakesProto.GameMessage msg = ProtoConverter.createAnnouncementMsg(
                        ann, session.getMyPlayerId(), engine.canJoin());

                messageSender.sendMulticast(msg);
            } catch (IOException e) {
                System.err.println("Failed to send announcement: " + e.getMessage());
            }
        }
    }


    public void handleDiscover(MessageHandler.DiscoverEvent event) {
        synchronized (hostLock) {
            if (session.isHost() && session.getGameEngine() != null) {
                sendAnnouncementTo(event.sender());
            }
        }
    }

    private void sendAnnouncementTo(InetSocketAddress target) {
        GameEngine engine = session.getGameEngine();
        if (!session.isHost() || engine == null) return;

        try {
            GameAnnouncement ann = new GameAnnouncement(
                    session.getGameName(), session.getGameConfig(),
                    engine.getState().getPlayers(), engine.canJoin(), null);

            SnakesProto.GameMessage msg = ProtoConverter.createAnnouncementMsg(
                    ann, session.getMyPlayerId(), engine.canJoin());
            messageSender.sendNoAck(msg, target);
        } catch (IOException e) {
            System.err.println("Failed to send announcement to " + target);
        }
    }

    public void handleJoin(MessageHandler.JoinEvent event) {
        synchronized (hostLock) {
            if (!session.isHost() || session.getGameEngine() == null) {
                return;
            }

            SnakesProto.GameMessage.JoinMsg join = event.join();
            InetSocketAddress senderAddr = event.sender();
            long msgSeq = event.msgSeq();

            if (session.getGameName() == null || !session.getGameName().equals(join.getGameName())) {
                sendError("Игра не найдена: " + join.getGameName(), senderAddr);
                return;
            }

            if (playerRegistry.isDuplicateJoinSeq(senderAddr, msgSeq)) {
                int existingId = playerRegistry.findPlayerIdByAddress(senderAddr);
                if (existingId != -1) {
                    sendAckWithId(msgSeq, existingId, senderAddr);
                    sendStateTo(senderAddr);
                    playerRegistry.registerNewPlayer(senderAddr);
                    return;
                } else {
                    playerRegistry.removeJoinSeq(senderAddr, msgSeq);
                }
            }

            if (playerRegistry.isJoinCooldownActive(senderAddr)) {
                int existingId = playerRegistry.findPlayerIdByAddress(senderAddr);
                if (existingId != -1) {
                    playerRegistry.recordJoinSeq(senderAddr, msgSeq);
                    sendAckWithId(msgSeq, existingId, senderAddr);
                    sendStateTo(senderAddr);
                    playerRegistry.registerNewPlayer(senderAddr);
                }
                return;
            }

            int existingActiveId = playerRegistry.findActivePlayerIdByAddress(senderAddr);
            if (existingActiveId != -1) {
                Optional<GamePlayer> existingPlayer = session.getGameEngine().getState()
                        .getPlayerById(existingActiveId);
                if (existingPlayer.isPresent() && existingPlayer.get().getRole() != NodeRole.VIEWER) {
                    playerRegistry.recordJoinSeq(senderAddr, msgSeq);
                    sendAckWithId(msgSeq, existingActiveId, senderAddr);
                    sendStateTo(senderAddr);
                    playerRegistry.registerNewPlayer(senderAddr);
                    return;
                }
            }

            playerRegistry.recordJoinSeq(senderAddr, msgSeq);
            playerRegistry.recordJoinTime(senderAddr);

            NodeRole requestedRole = ProtoConverter.fromProto(join.getRequestedRole());

            int viewerIdToRejoin = playerRegistry.findViewerIdByAddress(senderAddr);

            if (viewerIdToRejoin != -1 && requestedRole != NodeRole.VIEWER) {
                handleViewerRejoin(viewerIdToRejoin, senderAddr, msgSeq);
                return;
            }

            if (requestedRole == NodeRole.VIEWER) {
                createViewer(join.getPlayerName(), senderAddr, msgSeq);
            } else {
                createPlayer(join.getPlayerName(), senderAddr, msgSeq);
            }
        }
    }

    private void handleViewerRejoin(int playerId, InetSocketAddress senderAddr, long msgSeq) {
        GameEngine engine = session.getGameEngine();
        boolean shouldBeDeputy = (session.getDeputyAddress() == null && session.getDeputyPlayerId() == -1);
        NodeRole assignedRole = shouldBeDeputy ? NodeRole.DEPUTY : NodeRole.NORMAL;

        Optional<GamePlayer> playerOpt = engine.getState().getPlayerById(playerId);
        if (playerOpt.isPresent()) {
            GamePlayer player = playerOpt.get();

            Optional<Snake> snake = engine.spawnSnakeForPlayer(playerId);

            if (snake.isPresent()) {
                player.setRole(assignedRole);
                player.setAddress(senderAddr);

                playerRegistry.registerNewPlayer(senderAddr);
                session.getPlayerAddresses().put(playerId, senderAddr);

                sendAckWithId(msgSeq, playerId, senderAddr);

                if (shouldBeDeputy) {
                    assignDeputy(playerId, senderAddr);
                }

                session.setCurrentState(engine.getState());
                networkManager.notifyStateUpdate();

                executor.schedule(() -> {
                    sendStateTo(senderAddr);
                    broadcastState();
                }, 50, TimeUnit.MILLISECONDS);
            } else {
                sendError("Нет места на поле", senderAddr);
            }
        }
    }

    private void createViewer(String playerName, InetSocketAddress senderAddr, long msgSeq) {
        GameEngine engine = session.getGameEngine();

        int newId = engine.getNextPlayerId();
        GamePlayer viewer = GamePlayer.newPlayer(newId, playerName,
                senderAddr, NodeRole.VIEWER, PlayerType.HUMAN);
        engine.getState().addPlayer(viewer);
        session.getPlayerAddresses().put(newId, senderAddr);

        playerRegistry.registerNewPlayer(senderAddr);

        sendAckWithId(msgSeq, newId, senderAddr);

        executor.schedule(() -> sendStateTo(senderAddr), 50, TimeUnit.MILLISECONDS);
    }

    private void createPlayer(String playerName, InetSocketAddress senderAddr, long msgSeq) {
        GameEngine engine = session.getGameEngine();

        int newId = engine.getNextPlayerId();
        boolean shouldBeDeputy = (session.getDeputyAddress() == null && session.getDeputyPlayerId() == -1);
        NodeRole assignedRole = shouldBeDeputy ? NodeRole.DEPUTY : NodeRole.NORMAL;

        GamePlayer newPlayer = GamePlayer.newPlayer(newId, playerName,
                senderAddr, assignedRole, PlayerType.HUMAN);

        Optional<Snake> snake = engine.addPlayer(newPlayer);

        if (snake.isPresent()) {
            session.getPlayerAddresses().put(newId, senderAddr);
            playerRegistry.registerNewPlayer(senderAddr);

            sendAckWithId(msgSeq, newId, senderAddr);

            if (shouldBeDeputy) {
                assignDeputy(newId, senderAddr);
            }

            session.setCurrentState(engine.getState());
            networkManager.notifyStateUpdate();

            executor.schedule(() -> {
                sendStateTo(senderAddr);
                broadcastState();
            }, 50, TimeUnit.MILLISECONDS);
        } else {
            playerRegistry.removeJoinSeq(senderAddr, msgSeq);
            sendError("Нет места на поле", senderAddr);
        }
    }

    public void handleSteer(MessageHandler.SteerEvent event) {
        networkManager.sendAck(event.msgSeq(), event.sender());

        synchronized (hostLock) {
            if (!session.isHost() || session.getGameEngine() == null) return;

            int playerId = playerRegistry.findPlayerIdByAddress(event.sender());

            if (playerId == -1 && event.senderId() > 0) {
                playerId = event.senderId();

                Optional<GamePlayer> playerOpt = session.getGameEngine().getState().getPlayerById(playerId);
                if (playerOpt.isPresent()) {
                    session.getPlayerAddresses().put(playerId, event.sender());
                    playerRegistry.registerNewPlayer(event.sender());
                    playerOpt.get().setAddress(event.sender());
                } else {
                    return;
                }
            }

            if (playerId != -1) {
                nodeTracker.updateLastSeen(event.sender());
                session.getGameEngine().applySteer(playerId, event.direction(), event.msgSeq());
            }
        }
    }

    public void handlePing(MessageHandler.PingEvent event) {
        networkManager.sendAck(event.msgSeq(), event.sender());

        synchronized (hostLock) {
            if (!session.isHost()) return;

            int playerId = playerRegistry.findPlayerIdByAddress(event.sender());

            if (playerId == -1 && event.senderId() > 0) {
                playerId = event.senderId();

                if (session.getGameEngine() != null) {
                    Optional<GamePlayer> playerOpt = session.getGameEngine().getState().getPlayerById(playerId);
                    if (playerOpt.isPresent()) {
                        session.getPlayerAddresses().put(playerId, event.sender());
                        playerRegistry.registerNewPlayer(event.sender());
                        playerOpt.get().setAddress(event.sender());
                    }
                }
            }

            if (playerId != -1) {
                nodeTracker.updateLastSeen(event.sender());
            }
        }
    }

    public void handleRoleChange(MessageHandler.RoleChangeEvent event) {
        synchronized (hostLock) {
            if (event.senderRole() == NodeRole.VIEWER
                    && event.receiverRole() == null
                    && event.receiverId() <= 0) {
                handlePlayerLeave(event.senderId(), event.sender());
            }
        }
    }

    public void applySteer(Direction direction) {
        synchronized (hostLock) {
            if (session.isHost() && session.getGameEngine() != null) {
                session.getGameEngine().applySteerLocal(session.getMyPlayerId(), direction);
            }
        }
    }


    private void handlePlayerLeave(int playerId, InetSocketAddress addr) {
        if (!session.isHost() || session.getGameEngine() == null) return;

        boolean wasDeputy = (playerId == session.getDeputyPlayerId());

        playerRegistry.removePlayer(addr);
        messageSender.removeTarget(addr);

        detachPlayerKeepZombie(playerId);

        if (wasDeputy) {
            session.clearDeputy();
            selectNewDeputy();
        }

        session.setCurrentState(session.getGameEngine().getState());
        networkManager.notifyStateUpdate();
        broadcastState();

        checkGameEnd();
    }

    private void detachPlayerKeepZombie(int playerId) {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        engine.getState().getSnakeByPlayerId(playerId)
                .ifPresent(s -> s.setState(SnakeState.ZOMBIE));

        engine.getState().getPlayerById(playerId)
                .ifPresent(p -> p.setRole(NodeRole.VIEWER));

        session.setCurrentState(engine.getState());
    }

    public void leaveGame() {
        synchronized (hostLock) {
            if (!session.isHost() || session.getGameEngine() == null) return;

            boolean hasOtherActive = session.getGameEngine().getState().getPlayers().stream()
                    .anyMatch(p -> p.getId() != session.getMyPlayerId() &&
                            (p.getRole() == NodeRole.NORMAL || p.getRole() == NodeRole.DEPUTY));

            if (!hasOtherActive) {
                notifyAllGameOver();
                return;
            }

            session.getPlayerAddresses().remove(session.getMyPlayerId());
            detachPlayerKeepZombie(session.getMyPlayerId());
            session.setCurrentState(session.getGameEngine().getState());
            broadcastState();

            if (session.getDeputyAddress() != null && session.getDeputyPlayerId() != -1) {
                sendStateTo(session.getDeputyAddress());
                sendRoleChange(NodeRole.VIEWER, NodeRole.MASTER,
                        session.getDeputyPlayerId(), session.getDeputyAddress());
            } else {
                for (GamePlayer p : session.getGameEngine().getState().getPlayers()) {
                    if (p.getRole() == NodeRole.NORMAL) {
                        InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
                        if (addr != null) {
                            sendStateTo(addr);
                            sendRoleChange(NodeRole.VIEWER, NodeRole.MASTER, p.getId(), addr);
                            break;
                        }
                    }
                }
            }

            stopTasks();
        }
    }


    private void onPlayerDeath(int playerId, NodeRole oldRole) {
        System.out.println("Player " + playerId + " died (was " + oldRole + ")");

        synchronized (hostLock) {
            GameEngine engine = session.getGameEngine();
            if (engine == null) return;

            InetSocketAddress addr = session.getPlayerAddresses().get(playerId);

            if (oldRole == NodeRole.DEPUTY || playerId == session.getDeputyPlayerId()) {
                session.clearDeputy();
                selectNewDeputy();
            }

            if (addr != null && playerId != session.getMyPlayerId()) {
                sendRoleChange(null, NodeRole.VIEWER, playerId, addr);
            }

            if (playerId == session.getMyPlayerId() && session.getMyRole() == NodeRole.MASTER) {
                engine.getState().getPlayerById(session.getMyPlayerId())
                        .ifPresent(p -> p.setRole(NodeRole.VIEWER));

                boolean hasOtherActive = engine.getState().getPlayers().stream()
                        .anyMatch(p -> p.getId() != session.getMyPlayerId() &&
                                (p.getRole() == NodeRole.NORMAL || p.getRole() == NodeRole.DEPUTY));

                if (!hasOtherActive) {
                    session.setCurrentState(engine.getState());
                    broadcastState();
                    networkManager.notifyStateUpdate();
                    executor.schedule(this::notifyAllGameOver, 100, TimeUnit.MILLISECONDS);
                    return;
                }

                session.setPendingMasterDeathHandoff(true);
                session.setPendingDeputyAddr(session.getDeputyAddress());
                session.setPendingDeputyId(session.getDeputyPlayerId());
                return;
            }

            session.setCurrentState(engine.getState());
            networkManager.notifyStateUpdate();
            broadcastState();
        }
    }

    private void handoffMasterToDeputy(int depId, InetSocketAddress depAddr) {
        synchronized (hostLock) {
            GameEngine engine = session.getGameEngine();
            if (engine == null) {
                networkManager.notifyGameOver();
                return;
            }

            if (depAddr == null || depId <= 0) {
                boolean hasOtherActive = engine.getState().getPlayers().stream()
                        .anyMatch(p -> p.getId() != session.getMyPlayerId() &&
                                (p.getRole() == NodeRole.NORMAL || p.getRole() == NodeRole.DEPUTY));

                if (!hasOtherActive) {
                    notifyAllGameOver();
                    return;
                }

                for (GamePlayer p : engine.getState().getPlayers()) {
                    if (p.getId() != session.getMyPlayerId() && p.getRole() == NodeRole.NORMAL) {
                        InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
                        if (addr != null) {
                            depId = p.getId();
                            depAddr = addr;
                            break;
                        }
                    }
                }

                if (depAddr == null || depId <= 0) {
                    notifyAllGameOver();
                    return;
                }
            }

            sendStateTo(depAddr);
            sendRoleChange(NodeRole.VIEWER, NodeRole.MASTER, depId, depAddr);

            stopTasks();

            session.setHost(false);
            session.setMasterAddress(depAddr);
            session.setLastReceivedStateOrder(-1);
            session.setLastStateSender(null);
            session.setSwitchingToDeputy(false);
            session.clearDeputy();
            session.setMyRole(NodeRole.VIEWER);

            nodeTracker.addNode(session.getMasterAddress());
            nodeTracker.updateLastSeen(session.getMasterAddress());

            networkManager.notifyRoleChange();
        }
    }

    private void checkGameEnd() {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        boolean hasActivePlayers = engine.getState().getPlayers().stream()
                .anyMatch(p -> p.getRole() == NodeRole.MASTER
                        || p.getRole() == NodeRole.NORMAL
                        || p.getRole() == NodeRole.DEPUTY);

        if (!hasActivePlayers) {
            notifyAllGameOver();
        }
    }

    private void notifyAllGameOver() {
        System.out.println("=== GAME OVER ===");

        stopTasks();
        session.setHost(false);

        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        List<GamePlayer> players = new ArrayList<>(engine.getState().getPlayers());
        for (GamePlayer p : players) {
            if (p.getId() == session.getMyPlayerId()) continue;
            InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
            if (addr != null) {
                sendError("GAME_OVER", addr);
            }
        }

        executor.schedule(networkManager::notifyGameOver, 200, TimeUnit.MILLISECONDS);
    }


    private void assignDeputy(int playerId, InetSocketAddress addr) {
        session.setDeputyPlayerId(playerId);
        session.setDeputyAddress(addr);

        GameEngine engine = session.getGameEngine();
        if (engine != null) {
            engine.setPlayerRole(playerId, NodeRole.DEPUTY);
            engine.getState().getPlayerById(playerId)
                    .ifPresent(p -> p.setRole(NodeRole.DEPUTY));
            session.setCurrentState(engine.getState());
        }

        sendRoleChange(null, NodeRole.DEPUTY, playerId, addr);
        networkManager.notifyStateUpdate();
    }

    private void selectNewDeputy() {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;

        for (GamePlayer p : engine.getState().getPlayers()) {
            if (p.getRole() == NodeRole.NORMAL && p.getId() != session.getMyPlayerId()) {
                InetSocketAddress addr = session.getPlayerAddresses().get(p.getId());
                if (addr != null && !nodeTracker.isTimedOut(addr)) {
                    assignDeputy(p.getId(), addr);
                    return;
                }
            }
        }
    }


    private void maintenance() {
        GameConfig config = session.getGameConfig();
        if (config == null) return;

        long pingInterval = Math.max(config.getPingDelayMs(), 100);

        messageSender.retransmitUnconfirmed(pingInterval);

        checkTimeouts();

        sendPingsIfNeeded(pingInterval);
    }

    private void checkTimeouts() {
        synchronized (hostLock) {
            if (!session.isHost() || session.getGameEngine() == null) return;

            List<InetSocketAddress> timedOut = playerRegistry.findTimedOutNodes();

            for (InetSocketAddress addr : timedOut) {
                handleNodeTimeout(addr);
            }
        }
    }

    private void handleNodeTimeout(InetSocketAddress addr) {
        int playerId = playerRegistry.findPlayerIdByAddress(addr);
        if (playerId == -1) {
            playerRegistry.removePlayer(addr);
            return;
        }

        System.out.println("Node timeout: player " + playerId + " at " + addr);

        boolean wasDeputy = (playerId == session.getDeputyPlayerId());

        session.getPlayerAddresses().remove(playerId);
        playerRegistry.removePlayer(addr);
        messageSender.removeTarget(addr);

        detachPlayerKeepZombie(playerId);

        if (wasDeputy) {
            session.clearDeputy();
            selectNewDeputy();
        }

        session.setCurrentState(session.getGameEngine().getState());
        networkManager.notifyStateUpdate();
        broadcastState();

        checkGameEnd();
    }

    private void sendPingsIfNeeded(long pingInterval) {
        for (var entry : session.getPlayerAddresses().entrySet()) {
            if (messageSender.needsPing(entry.getValue(), pingInterval)) {
                sendPing(entry.getValue());
            }
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


    private void sendStateTo(InetSocketAddress target) {
        GameEngine engine = session.getGameEngine();
        if (engine == null) return;
        try {
            SnakesProto.GameMessage.Builder msg =
                    ProtoConverter.createStateMsg(engine.getState(), session.getMyPlayerId());
            messageSender.sendWithAck(msg, target);
        } catch (IOException e) {
            System.err.println("Failed to send state to " + target + ": " + e.getMessage());
        }
    }

    private void sendAckWithId(long msgSeq, int receiverId, InetSocketAddress target) {
        try {
            int senderId = Math.max(session.getMyPlayerId(), 0);
            SnakesProto.GameMessage ack = ProtoConverter.createAckMsg(msgSeq, senderId, receiverId);
            messageSender.sendNoAck(ack, target);
        } catch (IOException e) {
            System.err.println("Failed to send ACK with ID");
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

    private void sendError(String message, InetSocketAddress target) {
        try {
            SnakesProto.GameMessage.Builder msg = ProtoConverter.createErrorMsg(message);
            messageSender.sendWithAck(msg, target);
        } catch (IOException e) {
            System.err.println("Failed to send error");
        }
    }
}