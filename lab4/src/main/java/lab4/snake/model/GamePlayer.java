package lab4.snake.model;

import java.net.InetSocketAddress;
import java.util.Objects;

public class GamePlayer {
    private final String name;
    private final int id;
    private InetSocketAddress address;
    private NodeRole role;
    private final PlayerType type;
    private int score;

    public GamePlayer(int id, String name, InetSocketAddress address,
                      NodeRole role, PlayerType type, int score) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.role = role;
        this.type = type;
        this.score = score;
    }

    public static GamePlayer newPlayer(int id, String name, InetSocketAddress address,
                                       NodeRole role, PlayerType type) {
        return new GamePlayer(id, name, address, role, type, 0);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public NodeRole getRole() {
        return role;
    }

    public void setRole(NodeRole role) {
        this.role = role;
    }

    public PlayerType getType() {
        return type;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public boolean isPlaying() {
        return role != NodeRole.VIEWER;
    }
}