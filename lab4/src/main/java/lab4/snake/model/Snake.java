package lab4.snake.model;

import java.util.ArrayList;
import java.util.List;

public class Snake {
    private final int playerId;
    private List<Coord> keyPoints;
    private Direction headDirection;
    private SnakeState state;

    public Snake(int playerId, List<Coord> keyPoints,
                 Direction headDirection, SnakeState state) {
        this.playerId = playerId;
        this.keyPoints = new ArrayList<>(keyPoints);
        this.headDirection = headDirection;
        this.state = state;
    }

    public static Snake createNew(int playerId, Coord head, Direction tailDirection) {
        List<Coord> points = new ArrayList<>();
        points.add(head);
        points.add(tailDirection.toOffset());

        Direction headDir = tailDirection.opposite();
        return new Snake(playerId, points, headDir, SnakeState.ALIVE);
    }

    public int getPlayerId() {
        return playerId;
    }

    public Direction getHeadDirection() {
        return headDirection;
    }

    public void setHeadDirection(Direction direction) {
        if (!direction.isOpposite(this.headDirection)) {
            this.headDirection = direction;
        }
    }

    public SnakeState getState() {
        return state;
    }

    public void setState(SnakeState state) {
        this.state = state;
    }

    public List<Coord> getKeyPoints() {
        return new ArrayList<>(keyPoints);
    }

    public Coord getHead() {
        return keyPoints.get(0);
    }


    public List<Coord> getAllCells(int fieldWidth, int fieldHeight) {
        List<Coord> cells = new ArrayList<>();

        Coord current = keyPoints.get(0);
        cells.add(current.normalize(fieldWidth, fieldHeight));

        for (int i = 1; i < keyPoints.size(); i++) {
            Coord offset = keyPoints.get(i);

            int dx = Integer.signum(offset.x());
            int dy = Integer.signum(offset.y());
            int steps = Math.max(Math.abs(offset.x()), Math.abs(offset.y()));

            for (int step = 0; step < steps; step++) {
                current = new Coord(current.x() + dx, current.y() + dy);
                cells.add(current.normalize(fieldWidth, fieldHeight));
            }
        }

        return cells;
    }

    public List<Coord> getBodyCells(int fieldWidth, int fieldHeight) {
        List<Coord> allCells = getAllCells(fieldWidth, fieldHeight);
        return allCells.subList(1, allCells.size());
    }


    public void move(boolean ateFood, int fieldWidth, int fieldHeight) {
        Coord oldHead = keyPoints.get(0);
        Coord newHead = oldHead.move(headDirection).normalize(fieldWidth, fieldHeight);

        List<Coord> newKeyPoints = new ArrayList<>();
        newKeyPoints.add(newHead);

        Coord headOffset = calculateOffset(newHead, oldHead, fieldWidth, fieldHeight);

        if (keyPoints.size() > 1) {
            Coord firstSegment = keyPoints.get(1);
            if (isSameDirection(headOffset, firstSegment)) {
                newKeyPoints.add(new Coord(
                        headOffset.x() + firstSegment.x(),
                        headOffset.y() + firstSegment.y()
                ));
                for (int i = 2; i < keyPoints.size(); i++) {
                    newKeyPoints.add(keyPoints.get(i));
                }
            } else {
                newKeyPoints.add(headOffset);
                for (int i = 1; i < keyPoints.size(); i++) {
                    newKeyPoints.add(keyPoints.get(i));
                }
            }
        } else {
            newKeyPoints.add(headOffset);
        }

        if (!ateFood) {
            shortenTail(newKeyPoints);
        }

        this.keyPoints = newKeyPoints;
    }

    private Coord calculateOffset(Coord from, Coord to, int width, int height) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();

        if (dx > width / 2) dx -= width;
        else if (dx < -width / 2) dx += width;

        if (dy > height / 2) dy -= height;
        else if (dy < -height / 2) dy += height;

        return new Coord(dx, dy);
    }

    private boolean isSameDirection(Coord a, Coord b) {
        return Integer.signum(a.x()) == Integer.signum(b.x()) &&
                Integer.signum(a.y()) == Integer.signum(b.y()) &&
                (a.x() == 0) == (b.x() == 0) &&
                (a.y() == 0) == (b.y() == 0);
    }

    private void shortenTail(List<Coord> points) {
        if (points.size() < 2) return;

        int lastIdx = points.size() - 1;
        Coord lastSegment = points.get(lastIdx);

        int length = Math.max(Math.abs(lastSegment.x()), Math.abs(lastSegment.y()));

        if (length <= 1) {
            points.remove(lastIdx);
        } else {
            int dx = Integer.signum(lastSegment.x());
            int dy = Integer.signum(lastSegment.y());
            points.set(lastIdx, new Coord(
                    lastSegment.x() - dx,
                    lastSegment.y() - dy
            ));
        }
    }

    public Snake copy() {
        return new Snake(playerId, new ArrayList<>(keyPoints), headDirection, state);
    }
}
