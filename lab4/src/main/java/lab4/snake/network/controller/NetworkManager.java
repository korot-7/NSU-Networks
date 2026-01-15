package lab4.snake.network.controller;

import javafx.application.Platform;

import lab4.protobuf.SnakesProto;
import lab4.snake.model.*;
import lab4.snake.network.*;
import lab4.snake.util.Config;
import lab4.snake.util.ProtoConverter;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


public class NetworkManager {

    private final ScheduledExecutorService executor;
    private final GameSession session;
    private final GameAnnouncementTracker announcementTracker;

    private UDPSocket unicastSocket;
    private MulticastReceiver multicastReceiver;
    private UnicastReceiver unicastReceiver;
    private MessageSender messageSender;
    private MessageHandler messageHandler;
    private NodeTracker nodeTracker;
    private PlayerRegistry playerRegistry;

    private MasterController masterController;
    private NormalController normalController;

    private Future<?> multicastReceiverTask;
    private Future<?> unicastReceiverTask;
    private ScheduledFuture<?> cleanupTask;

    private Consumer<GameState> onStateUpdate;
    private Consumer<List<GameAnnouncement>> onGamesListUpdate;
    private Consumer<String> onError;
    private Consumer<NodeRole> onRoleChange;
    private Runnable onGameOver;

    private volatile boolean running = false;

    private static final long MIN_NODE_TIMEOUT_MS = 1500;

    public NetworkManager() {
        this.executor = Executors.newScheduledThreadPool(4);
        this.session = new GameSession();
        this.announcementTracker = new GameAnnouncementTracker();
    }


    public void start() throws IOException {
        if (running) return;

        unicastSocket = new UDPSocket();
        System.out.println("Unicast socket on port " + unicastSocket.getLocalPort());

        messageSender = new MessageSender(unicastSocket);
        messageHandler = new MessageHandler();
        nodeTracker = new NodeTracker(MIN_NODE_TIMEOUT_MS);
        playerRegistry = new PlayerRegistry(session, nodeTracker);

        masterController = new MasterController(this, session, playerRegistry,
                messageSender, nodeTracker, executor);
        normalController = new NormalController(this, session,
                messageSender, nodeTracker, executor);

        setupMessageHandlers();

        try {
            multicastReceiver = new MulticastReceiver(this::handleMulticastMessage);
            if (multicastReceiver.isJoined()) {
                multicastReceiverTask = executor.submit(multicastReceiver);
                messageSender.setMulticastSocket(
                        multicastReceiver.getSocketForSend());
            }
        } catch (IOException e) {
            System.err.println("Multicast setup failed: " + e.getMessage());
        }

        unicastReceiver = new UnicastReceiver(unicastSocket, this::handleUnicastMessage);
        unicastReceiverTask = executor.submit(unicastReceiver);

        cleanupTask = executor.scheduleAtFixedRate(
                this::cleanupExpiredAnnouncements, 1, 1, TimeUnit.SECONDS);

        running = true;
        System.out.println("NetworkManager started on port " + unicastSocket.getLocalPort());
    }

    public void stop() {
        System.out.println("NetworkManager stopping...");
        running = false;

        masterController.stopTasks();
        normalController.stopTasks();

        cancelTask(multicastReceiverTask);
        cancelTask(unicastReceiverTask);
        cancelTask(cleanupTask);

        if (multicastReceiver != null) multicastReceiver.stop();
        if (unicastReceiver != null) unicastReceiver.stop();
        if (unicastSocket != null) unicastSocket.close();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("NetworkManager stopped");
    }

    private void cancelTask(Future<?> task) {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
    }


    public void discoverGames() {
        try {
            SnakesProto.GameMessage msg = ProtoConverter.createDiscoverMsg();
            messageSender.sendMulticast(msg);
        } catch (IOException e) {
            System.err.println("Discover failed: " + e.getMessage());
        }
    }

    public void createGame(GameConfig config, String playerName, String gameName) {
        masterController.createGame(config, playerName, gameName);
        nodeTracker.setTimeoutMs(session.getGameConfig().getNodeTimeoutMs());
    }

    public void joinGame(GameAnnouncement game, String playerName, boolean asViewer) {
        normalController.joinGame(game, playerName, asViewer);
        nodeTracker.setTimeoutMs(session.getGameConfig().getNodeTimeoutMs());
    }

    public void steer(Direction direction) {
        if (session.getMyPlayerId() <= 0) return;
        if (!session.hasAliveSnake()) return;

        if (session.isHost()) {
            masterController.applySteer(direction);
        } else {
            normalController.sendSteer(direction);
        }
    }

    public void leaveGame() {

        String leavingGameName = session.getGameName();

        if (session.isHost()) {
            masterController.leaveGame();
        } else {
            normalController.leaveGame();
        }

        resetGameState();

        if (leavingGameName != null) {
            announcementTracker.removeGame(leavingGameName);
            notifyGamesListUpdate();
        }
    }


    private void setupMessageHandlers() {
        messageHandler.setOnAnnouncement(this::handleAnnouncement);
        messageHandler.setOnDiscover(e -> masterController.handleDiscover(e));
        messageHandler.setOnAck(this::handleAck);
        messageHandler.setOnState(e -> normalController.handleState(e));
        messageHandler.setOnJoin(e -> masterController.handleJoin(e));
        messageHandler.setOnSteer(e -> masterController.handleSteer(e));
        messageHandler.setOnPing(e -> masterController.handlePing(e));
        messageHandler.setOnRoleChange(this::handleRoleChange);
        messageHandler.setOnError(this::handleError);
    }

    private void handleMulticastMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
        messageHandler.handle(message, sender);
    }

    private void handleUnicastMessage(SnakesProto.GameMessage message, InetSocketAddress sender) {
        nodeTracker.updateLastSeen(sender);
        messageHandler.handle(message, sender);
    }

    private void handleAnnouncement(MessageHandler.AnnouncementEvent event) {
        for (SnakesProto.GameAnnouncement protoAnn : event.announcement().getGamesList()) {
            GameAnnouncement announcement = ProtoConverter.fromProto(protoAnn, event.sender());

            if (session.isHost() && session.getGameName() != null &&
                    session.getGameName().equals(announcement.getGameName())) {
                continue;
            }

            InetSocketAddress masterAddr = announcement.getMasterAddress();
            if (masterAddr != null && masterAddr.getPort() == Config.MULTICAST_PORT) {
                continue;
            }

            announcementTracker.updateGame(announcement);
        }
        notifyGamesListUpdate();
    }

    private void handleAck(MessageHandler.AckEvent event) {
        messageSender.onAckReceived(event.msgSeq());

        if (!session.isHost() && session.getMasterAddress() != null &&
                session.getMasterAddress().equals(event.sender())) {
            nodeTracker.updateLastSeen(session.getMasterAddress());
        }

        if (session.isHost()) {
            nodeTracker.updateLastSeen(event.sender());
        }

        if (event.receiverId() > 0 && (session.getMyPlayerId() == -1 || session.isJoining())) {
            session.setMyPlayerId(event.receiverId());
            System.out.println("=== MY PLAYER ID SET: " + event.receiverId() + " ===");

            if (session.getCurrentState() != null) {
                session.getCurrentState().getPlayerById(event.receiverId()).ifPresent(player -> {
                    if (session.getMyRole() != player.getRole()) {
                        session.setMyRole(player.getRole());
                        notifyRoleChange();
                    }
                });
            }

            session.setJoining(false);
            normalController.cancelJoinWatchdog();
        }
    }

    private void handleRoleChange(MessageHandler.RoleChangeEvent event) {
        sendAck(event.msgSeq(), event.sender());

        if (session.isHost()) {
            masterController.handleRoleChange(event);
        } else {
            normalController.handleRoleChange(event);
        }
    }

    private void handleError(MessageHandler.ErrorEvent event) {
        sendAck(event.msgSeq(), event.sender());

        session.setJoining(false);
        normalController.cancelJoinWatchdog();

        if ("GAME_OVER".equals(event.errorMessage())) {
            announcementTracker.removeGame(session.getGameName());
            notifyGamesListUpdate();
            notifyGameOver();
            return;
        }

        notifyError(event.errorMessage());
    }



    public void sendAck(long msgSeq, InetSocketAddress target) {
        try {
            int senderId = Math.max(session.getMyPlayerId(), 0);
            SnakesProto.GameMessage ack = ProtoConverter.createAckMsg(msgSeq, senderId, -1);
            messageSender.sendNoAck(ack, target);
        } catch (IOException e) {
            System.err.println("Failed to send ACK");
        }
    }

    public int getLocalPort() {
        return unicastSocket != null ? unicastSocket.getLocalPort() : -1;
    }

    public InetAddress getLocalAddress() {
        if (multicastReceiver != null) {
            InetAddress addr = multicastReceiver.getLocalAddress();
            if (addr != null) return addr;
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Failed to get local address: " + e.getMessage());
        }
        return null;
    }

    private void resetGameState() {
        masterController.stopTasks();
        normalController.stopTasks();

        session.reset();
        playerRegistry.clear();
        messageSender.clearPending();
    }

    private void cleanupExpiredAnnouncements() {
        if (announcementTracker.removeExpiredGames()) {
            notifyGamesListUpdate();
        }
    }


    public void notifyStateUpdate() {
        if (onStateUpdate != null && session.getCurrentState() != null) {
            Platform.runLater(() -> onStateUpdate.accept(session.getCurrentState()));
        }
    }

    public void notifyGamesListUpdate() {
        if (onGamesListUpdate != null) {
            Platform.runLater(() -> onGamesListUpdate.accept(announcementTracker.getAvailableGames()));
        }
    }

    public void notifyRoleChange() {
        if (onRoleChange != null) {
            Platform.runLater(() -> onRoleChange.accept(session.getMyRole()));
        }
    }

    public void notifyGameOver() {
        resetGameState();
        if (onGameOver != null) {
            Platform.runLater(onGameOver);
        }
    }

    public void notifyError(String message) {
        if (onError != null) {
            Platform.runLater(() -> onError.accept(message));
        }
    }


    public void setOnStateUpdate(Consumer<GameState> c) { this.onStateUpdate = c; }
    public void setOnGamesListUpdate(Consumer<List<GameAnnouncement>> c) { this.onGamesListUpdate = c; }
    public void setOnError(Consumer<String> c) { this.onError = c; }
    public void setOnRoleChange(Consumer<NodeRole> c) { this.onRoleChange = c; }
    public void setOnGameOver(Runnable c) { this.onGameOver = c; }

    public PlayerRegistry getPlayerRegistry() { return playerRegistry; }

    public NodeRole getMyRole() { return session.getMyRole(); }
    public int getMyPlayerId() { return session.getMyPlayerId(); }
    public GameState getCurrentState() { return session.getCurrentState(); }
    public String getGameName() { return session.getGameName(); }
    public boolean isRunning() { return running; }
    public boolean isInGame() { return session.isInGame(); }
    public boolean isHost() { return session.isHost(); }
    public MasterController getMasterController() { return masterController; }
}