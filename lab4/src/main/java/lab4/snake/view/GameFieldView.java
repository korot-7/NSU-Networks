package lab4.snake.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import lab4.snake.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameFieldView {
    private final Canvas canvas;
    private final GraphicsContext gc;

    private static final Color BACKGROUND_COLOR = Color.rgb(20, 20, 30);
    private static final Color GRID_COLOR = Color.rgb(40, 40, 50);
    private static final Color FOOD_COLOR = Color.rgb(255, 50, 50);
    private static final Color MY_SNAKE_COLOR = Color.rgb(50, 255, 50);
    private static final Color MY_SNAKE_HEAD_COLOR = Color.rgb(100, 255, 100);
    private static final Color ZOMBIE_COLOR = Color.rgb(100, 100, 100);

    private static final Color[] PLAYER_COLORS = {
            Color.rgb(50, 150, 255),
            Color.rgb(255, 200, 50),
            Color.rgb(255, 50, 200),
            Color.rgb(50, 255, 200),
            Color.rgb(200, 100, 255),
            Color.rgb(255, 150, 50),
    };

    private final Map<Integer, Color> playerColorMap = new HashMap<>();
    private int colorIndex = 0;

    public GameFieldView(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }


    public void render(GameState state, int myPlayerId) {
        if (state == null) return;

        GameConfig config = state.getConfig();
        if (config == null) return;

        int fieldWidth = config.width();
        int fieldHeight = config.height();

        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        if (canvasWidth <= 0 || canvasHeight <= 0) return;

        double cellWidth = canvasWidth / fieldWidth;
        double cellHeight = canvasHeight / fieldHeight;
        double cellSize = Math.min(cellWidth, cellHeight);

        double offsetX = (canvasWidth - cellSize * fieldWidth) / 2;
        double offsetY = (canvasHeight - cellSize * fieldHeight) / 2;

        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        drawGrid(fieldWidth, fieldHeight, cellSize, offsetX, offsetY);

        drawFood(state.getFoods(), cellSize, offsetX, offsetY);

        drawSnakes(state.getSnakes(), myPlayerId, fieldWidth, fieldHeight,
                cellSize, offsetX, offsetY);
    }

    private void drawGrid(int fieldWidth, int fieldHeight, double cellSize,
                          double offsetX, double offsetY) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);

        for (int x = 0; x <= fieldWidth; x++) {
            double px = offsetX + x * cellSize;
            gc.strokeLine(px, offsetY, px, offsetY + fieldHeight * cellSize);
        }

        for (int y = 0; y <= fieldHeight; y++) {
            double py = offsetY + y * cellSize;
            gc.strokeLine(offsetX, py, offsetX + fieldWidth * cellSize, py);
        }
    }

    private void drawFood(List<Coord> foods, double cellSize,
                          double offsetX, double offsetY) {
        gc.setFill(FOOD_COLOR);

        double padding = cellSize * 0.15;

        for (Coord food : foods) {
            double x = offsetX + food.x() * cellSize + padding;
            double y = offsetY + food.y() * cellSize + padding;
            double size = cellSize - padding * 2;

            gc.fillOval(x, y, size, size);
        }
    }

    private void drawSnakes(List<Snake> snakes, int myPlayerId,
                            int fieldWidth, int fieldHeight,
                            double cellSize, double offsetX, double offsetY) {
        for (Snake snake : snakes) {
            drawSnake(snake, myPlayerId, fieldWidth, fieldHeight,
                    cellSize, offsetX, offsetY);
        }
    }

    private void drawSnake(Snake snake, int myPlayerId,
                           int fieldWidth, int fieldHeight,
                           double cellSize, double offsetX, double offsetY) {
        boolean isMySnake = snake.getPlayerId() == myPlayerId;
        boolean isZombie = snake.getState() == SnakeState.ZOMBIE;

        Color bodyColor;
        Color headColor;

        if (isZombie) {
            bodyColor = ZOMBIE_COLOR;
            headColor = ZOMBIE_COLOR.brighter();
        } else if (isMySnake) {
            bodyColor = MY_SNAKE_COLOR;
            headColor = MY_SNAKE_HEAD_COLOR;
        } else {
            bodyColor = getPlayerColor(snake.getPlayerId());
            headColor = bodyColor.brighter();
        }

        List<Coord> cells = snake.getAllCells(fieldWidth, fieldHeight);

        if (cells.isEmpty()) return;

        double padding = cellSize * 0.05;
        double cornerRadius = cellSize * 0.3;

        gc.setFill(bodyColor);
        for (int i = 1; i < cells.size(); i++) {
            Coord cell = cells.get(i);
            double x = offsetX + cell.x() * cellSize + padding;
            double y = offsetY + cell.y() * cellSize + padding;
            double size = cellSize - padding * 2;

            gc.fillRoundRect(x, y, size, size, cornerRadius, cornerRadius);
        }

        gc.setFill(headColor);
        Coord head = cells.get(0);
        double hx = offsetX + head.x() * cellSize + padding;
        double hy = offsetY + head.y() * cellSize + padding;
        double hsize = cellSize - padding * 2;

        gc.fillRoundRect(hx, hy, hsize, hsize, cornerRadius, cornerRadius);

        drawEyes(head, snake.getHeadDirection(), cellSize, offsetX, offsetY);
    }

    private void drawEyes(Coord head, Direction direction, double cellSize,
                          double offsetX, double offsetY) {
        gc.setFill(Color.WHITE);

        double eyeSize = cellSize * 0.15;
        double eyeOffset = cellSize * 0.2;

        double cx = offsetX + head.x() * cellSize + cellSize / 2;
        double cy = offsetY + head.y() * cellSize + cellSize / 2;

        double eye1x, eye1y, eye2x, eye2y;

        switch (direction) {
            case UP -> {
                eye1x = cx - eyeOffset;
                eye1y = cy - eyeOffset;
                eye2x = cx + eyeOffset;
                eye2y = cy - eyeOffset;
            }
            case DOWN -> {
                eye1x = cx - eyeOffset;
                eye1y = cy + eyeOffset;
                eye2x = cx + eyeOffset;
                eye2y = cy + eyeOffset;
            }
            case LEFT -> {
                eye1x = cx - eyeOffset;
                eye1y = cy - eyeOffset;
                eye2x = cx - eyeOffset;
                eye2y = cy + eyeOffset;
            }
            case RIGHT -> {
                eye1x = cx + eyeOffset;
                eye1y = cy - eyeOffset;
                eye2x = cx + eyeOffset;
                eye2y = cy + eyeOffset;
            }
            default -> {
                return;
            }
        }

        gc.fillOval(eye1x - eyeSize/2, eye1y - eyeSize/2, eyeSize, eyeSize);
        gc.fillOval(eye2x - eyeSize/2, eye2y - eyeSize/2, eyeSize, eyeSize);


        gc.setFill(Color.BLACK);
        double pupilSize = eyeSize * 0.5;
        gc.fillOval(eye1x - pupilSize/2, eye1y - pupilSize/2, pupilSize, pupilSize);
        gc.fillOval(eye2x - pupilSize/2, eye2y - pupilSize/2, pupilSize, pupilSize);
    }

    private Color getPlayerColor(int playerId) {
        return playerColorMap.computeIfAbsent(playerId, id -> {
            Color color = PLAYER_COLORS[colorIndex % PLAYER_COLORS.length];
            colorIndex++;
            return color;
        });
    }
}
