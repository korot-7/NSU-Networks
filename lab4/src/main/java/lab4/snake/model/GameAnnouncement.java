package lab4.snake.model;

import lab4.snake.util.Config;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;

public class GameAnnouncement {
    private final String gameName;
    private final GameConfig config;
    private final List<GamePlayer> players;
    private final boolean canJoin;
    private final InetSocketAddress masterAddress;
    private long lastSeen;

    public GameAnnouncement(String gameName, GameConfig config, List<GamePlayer> players,
                            boolean canJoin, InetSocketAddress masterAddress) {
        this.gameName = gameName;
        this.config = config;
        this.players = players;
        this.canJoin = canJoin;
        this.masterAddress = masterAddress;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getGameName() {
        return gameName;
    }

    public GameConfig getConfig() {
        return config;
    }

    public List<GamePlayer> getPlayers() {
        return players;
    }

    public boolean canJoin() {
        return canJoin;
    }

    public InetSocketAddress getMasterAddress() {
        return masterAddress;
    }

    public int getPlayingCount() {
        return (int) players.stream()
                .filter(GamePlayer::isPlaying)
                .count();
    }

    public String getFieldSize() {
        return config.width() + "x" + config.height();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - lastSeen > Config.GAME_ANNOUNCEMENT_EXPIRE_MS;
    }
}
