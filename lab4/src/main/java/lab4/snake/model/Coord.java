package lab4.snake.model;

public record Coord(int x, int y) {
    public Coord move(Direction direction) {
        return switch (direction) {
            case UP -> new Coord(x, y - 1);
            case DOWN -> new Coord(x, y + 1);
            case LEFT -> new Coord(x - 1, y);
            case RIGHT -> new Coord(x + 1, y);
        };
    }

    public Coord normalize(int width, int height) {
        int normX = ((x % width) + width) % width;
        int normY = ((y % height) + height) % height;
        return new Coord(normX, normY);
    }
}