package lab4.snake.network;

import lab4.snake.model.GameAnnouncement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameAnnouncementTracker {

    private final Map<String, GameAnnouncement> games = new ConcurrentHashMap<>();

    public void updateGame(GameAnnouncement announcement) {
        games.put(announcement.getGameName(), announcement);
    }


    public List<GameAnnouncement> getAvailableGames() {
        return games.values().stream()
                .filter(g -> !g.isExpired())
                .toList();
    }


    public boolean removeExpiredGames() {
        int sizeBefore = games.size();
        games.entrySet().removeIf(e -> e.getValue().isExpired());
        int sizeAfter = games.size();
        return sizeBefore != sizeAfter;
    }

    public void removeGame(String gameName) {
        games.remove(gameName);
    }
}