package lab4.snake.game;

import lab4.snake.model.Coord;
import lab4.snake.model.Snake;
import lab4.snake.model.SnakeState;
import lab4.snake.util.Config;

import java.util.*;

public class FoodManager {
    private final GameField field;
    private final Random random;

    public FoodManager(GameField field) {
        this.field = field;
        this.random = new Random();
    }

    public int calculateRequiredFood(int foodStatic, int aliveSnakesCount) {
        return foodStatic + aliveSnakesCount;
    }


    public List<Coord> generateFood(List<Coord> currentFood, int requiredCount,
                                    Set<Coord> occupied) {
        int currentCount = currentFood.size();
        int toGenerate = requiredCount - currentCount;

        if (toGenerate <= 0) {
            return Collections.emptyList();
        }

        Set<Coord> allOccupied = new HashSet<>(occupied);
        allOccupied.addAll(currentFood);

        return field.getRandomFreeCells(toGenerate, allOccupied);
    }

    public List<Coord> snakeToFood(Snake deadSnake) {
        List<Coord> newFood = new ArrayList<>();
        List<Coord> cells = deadSnake.getAllCells(field.getWidth(), field.getHeight());

        for (Coord cell : cells) {
            if (random.nextDouble() < Config.FOOD_FROM_DEAD_SNAKE_PROBABILITY) {
                newFood.add(cell);
            }
        }

        return newFood;
    }

    public static int countAliveSnakes(List<Snake> snakes) {
        return (int) snakes.stream()
                .filter(s -> s.getState() == SnakeState.ALIVE)
                .count();
    }
}
