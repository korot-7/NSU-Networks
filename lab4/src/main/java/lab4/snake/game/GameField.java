package lab4.snake.game;

import lab4.snake.model.*;
import java.util.*;

public class GameField {
    private final int width;
    private final int height;

    public GameField(GameConfig config) {
        this.width = config.width();
        this.height = config.height();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Coord normalize(Coord coord) {
        return coord.normalize(width, height);
    }

    public Coord getNeighbor(Coord coord, Direction direction) {
        return normalize(coord.move(direction));
    }

    public List<Coord> getNeighbors(Coord coord) {
        List<Coord> neighbors = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            neighbors.add(getNeighbor(coord, dir));
        }
        return neighbors;
    }

    public Optional<Coord> findFreeSquare(int squareSize, Set<Coord> occupiedBySnakes,
                                          Set<Coord> occupiedByFood) {
        int halfSize = squareSize / 2;
        List<Coord> candidates = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Coord center = new Coord(x, y);

                if (isSquareFree(center, halfSize, occupiedBySnakes, occupiedByFood)) {
                    candidates.add(center);
                }
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(candidates.get(new Random().nextInt(candidates.size())));
    }

    private boolean isSquareFree(Coord center, int halfSize,
                                 Set<Coord> occupiedBySnakes, Set<Coord> occupiedByFood) {
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dy = -halfSize; dy <= halfSize; dy++) {
                Coord cell = normalize(new Coord(center.x() + dx, center.y() + dy));

                if (occupiedBySnakes.contains(cell)) {
                    return false;
                }
            }
        }

        if (occupiedByFood.contains(center)) {
            return false;
        }
        for (Coord neighbor : getNeighbors(center)) {
            if (occupiedByFood.contains(neighbor)) {
                return false;
            }
        }

        return true;
    }

    public List<Coord> getRandomFreeCells(int count, Set<Coord> occupied) {
        List<Coord> freeCells = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Coord cell = new Coord(x, y);
                if (!occupied.contains(cell)) {
                    freeCells.add(cell);
                }
            }
        }

        Collections.shuffle(freeCells);

        return freeCells.subList(0, Math.min(count, freeCells.size()));
    }
}
