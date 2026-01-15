package lab4.snake.game;

import lab4.snake.model.*;
import java.util.*;

public class CollisionDetector {
    private final GameField field;

    public CollisionDetector(GameField field) {
        this.field = field;
    }

    public static class CollisionResult {
        private final Set<Integer> deadSnakes;
        private final Map<Integer, Integer> scoreBonus;

        public CollisionResult() {
            this.deadSnakes = new HashSet<>();
            this.scoreBonus = new HashMap<>();
        }

        public void addDeath(int playerId) {
            deadSnakes.add(playerId);
        }

        public void addScore(int playerId, int bonus) {
            scoreBonus.merge(playerId, bonus, Integer::sum);
        }

        public Set<Integer> getDeadSnakes() {
            return deadSnakes;
        }

        public Map<Integer, Integer> getScoreBonus() {
            return scoreBonus;
        }

        public boolean hasDeath(int playerId) {
            return deadSnakes.contains(playerId);
        }
    }

    public CollisionResult checkCollisions(List<Snake> snakes) {
        CollisionResult result = new CollisionResult();
        int width = field.getWidth();
        int height = field.getHeight();

        Map<Coord, List<Snake>> headPositions = new HashMap<>();

        Map<Coord, Snake> bodyPositions = new HashMap<>();

        for (Snake snake : snakes) {
            Coord head = field.normalize(snake.getHead());
            headPositions.computeIfAbsent(head, k -> new ArrayList<>()).add(snake);

            List<Coord> bodyCells = snake.getBodyCells(width, height);
            for (Coord cell : bodyCells) {
                bodyPositions.put(cell, snake);
            }
        }

        for (Snake snake : snakes) {
            Coord head = field.normalize(snake.getHead());

            if (bodyPositions.containsKey(head)) {
                Snake hitSnake = bodyPositions.get(head);
                result.addDeath(snake.getPlayerId());

                if (hitSnake.getPlayerId() != snake.getPlayerId()) {
                    result.addScore(hitSnake.getPlayerId(), 1);
                }
            }

            List<Snake> headsAtPosition = headPositions.get(head);
            if (headsAtPosition.size() > 1) {
                for (Snake s : headsAtPosition) {
                    result.addDeath(s.getPlayerId());
                }
            }
        }

        return result;
    }

    public Set<Coord> getOccupiedBySnakes(List<Snake> snakes) {
        Set<Coord> occupied = new HashSet<>();
        int width = field.getWidth();
        int height = field.getHeight();

        for (Snake snake : snakes) {
            occupied.addAll(snake.getAllCells(width, height));
        }

        return occupied;
    }
}
