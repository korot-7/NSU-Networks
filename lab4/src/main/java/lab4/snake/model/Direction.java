package lab4.snake.model;

public enum Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT;

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public boolean isOpposite(Direction other) {
        return this.opposite() == other;
    }

    public Coord toOffset() {
        return switch (this) {
            case UP -> new Coord(0, -1);
            case DOWN -> new Coord(0, 1);
            case LEFT -> new Coord(-1, 0);
            case RIGHT -> new Coord(1, 0);
        };
    }
}