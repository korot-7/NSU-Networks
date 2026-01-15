package lab4.snake.game;

import lab4.snake.model.*;
import lab4.snake.util.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class GameEngine {
    private final GameState state;
    private final GameField field;
    private final CollisionDetector collisionDetector;
    private final FoodManager foodManager;

    private final Map<Integer, SteerCommand> pendingSteers;

    private BiConsumer<Integer, NodeRole> onPlayerDeath;

    private record SteerCommand(long msgSeq, Direction direction) {
    }

    public GameEngine(GameConfig config) {
        this.state = GameState.initial(config);
        this.field = new GameField(config);
        this.collisionDetector = new CollisionDetector(field);
        this.foodManager = new FoodManager(field);
        this.pendingSteers = new ConcurrentHashMap<>();
    }

    public GameEngine(GameState existingState) {
        this.state = existingState.copy();
        this.field = new GameField(existingState.getConfig());
        this.collisionDetector = new CollisionDetector(field);
        this.foodManager = new FoodManager(field);
        this.pendingSteers = new ConcurrentHashMap<>();
    }

    public void setOnPlayerDeath(BiConsumer<Integer, NodeRole> handler) {
        this.onPlayerDeath = handler;
    }

    public GameState getState() {
        return state;
    }

    public GameField getField() {
        return field;
    }

    public synchronized void applySteer(int playerId, Direction direction, long msgSeq) {
        pendingSteers.compute(playerId, (id, existing) -> {
            if (existing == null || msgSeq > existing.msgSeq) {
                return new SteerCommand(msgSeq, direction);
            }
            return existing;
        });
    }

    private final AtomicLong localSteerSeq = new AtomicLong(0);

    public void applySteerLocal(int playerId, Direction direction) {
        applySteer(playerId, direction, localSteerSeq.incrementAndGet());
    }

    public synchronized GameState tick() {
        applyPendingSteers();

        Set<Integer> willEat = determineWhoEats();

        moveSnakes(willEat);

        processEatenFood(willEat);

        CollisionDetector.CollisionResult collisions =
                collisionDetector.checkCollisions(state.getSnakes());

        processDeaths(collisions);

        processScores(collisions);

        replenishFood();

        state.incrementStateOrder();

        return state;
    }

    private void applyPendingSteers() {
        Map<Integer, SteerCommand> toApply = new HashMap<>(pendingSteers);
        pendingSteers.clear();

        for (Map.Entry<Integer, SteerCommand> entry : toApply.entrySet()) {
            int playerId = entry.getKey();
            Direction direction = entry.getValue().direction;

            state.getSnakeByPlayerId(playerId).ifPresent(snake -> {
                if (snake.getState() == SnakeState.ALIVE) {
                    snake.setHeadDirection(direction);
                }
            });
        }
        pendingSteers.clear();
    }

    private Set<Integer> determineWhoEats() {
        Set<Integer> willEat = new HashSet<>();
        Set<Coord> foodSet = new HashSet<>(state.getFoods());

        for (Snake snake : state.getSnakes()) {
            Coord nextHead = field.normalize(snake.getHead().move(snake.getHeadDirection()));
            if (foodSet.contains(nextHead)) {
                willEat.add(snake.getPlayerId());
            }
        }

        return willEat;
    }

    private void moveSnakes(Set<Integer> willEat) {
        for (Snake snake : state.getSnakes()) {
            boolean ateFood = willEat.contains(snake.getPlayerId());
            snake.move(ateFood, field.getWidth(), field.getHeight());
        }
    }

    private void processEatenFood(Set<Integer> eaters) {
        for (int playerId : eaters) {
            state.getSnakeByPlayerId(playerId).ifPresent(snake -> {
                Coord head = field.normalize(snake.getHead());
                state.removeFood(head);
                state.getPlayerById(playerId).ifPresent(player -> player.addScore(1));
            });
        }
    }

    private void processDeaths(CollisionDetector.CollisionResult collisions) {
        for (int deadPlayerId : collisions.getDeadSnakes()) {
            state.getSnakeByPlayerId(deadPlayerId).ifPresent(snake -> {
                List<Coord> newFood = foodManager.snakeToFood(snake);

                Set<Coord> existingFood = new HashSet<>(state.getFoods());
                for (Coord food : newFood) {
                    if (!existingFood.contains(food)) {
                        state.addFood(food);
                    }
                }

                state.removeSnake(deadPlayerId);

                state.getPlayerById(deadPlayerId).ifPresent(player -> {
                    NodeRole oldRole = player.getRole();

                    player.setRole(NodeRole.VIEWER);

                    if (onPlayerDeath != null) {
                        onPlayerDeath.accept(deadPlayerId, oldRole);
                    }
                });
            });
        }
    }

    private void processScores(CollisionDetector.CollisionResult collisions) {
        for (Map.Entry<Integer, Integer> entry : collisions.getScoreBonus().entrySet()) {
            int playerId = entry.getKey();
            int bonus = entry.getValue();

            if (!collisions.hasDeath(playerId)) {
                state.getPlayerById(playerId).ifPresent(player -> player.addScore(bonus));
            }
        }
    }

    private void replenishFood() {
        int aliveCount = FoodManager.countAliveSnakes(state.getSnakes());
        int required = foodManager.calculateRequiredFood(
                state.getConfig().foodStatic(), aliveCount);

        Set<Coord> occupied = collisionDetector.getOccupiedBySnakes(state.getSnakes());
        List<Coord> newFood = foodManager.generateFood(state.getFoods(), required, occupied);
        state.addFoods(newFood);
    }



    public Optional<Snake> addPlayer(GamePlayer player) {
        Set<Coord> occupiedBySnakes = collisionDetector.getOccupiedBySnakes(state.getSnakes());
        Set<Coord> occupiedByFood = new HashSet<>(state.getFoods());

        Optional<Coord> centerOpt = field.findFreeSquare(
                Config.NEW_SNAKE_SQUARE_SIZE, occupiedBySnakes, occupiedByFood);

        if (centerOpt.isEmpty()) {
            return Optional.empty();
        }

        Coord center = centerOpt.get();

        Direction[] directions = Direction.values();
        List<Direction> shuffled = new ArrayList<>(Arrays.asList(directions));
        Collections.shuffle(shuffled);

        for (Direction tailDir : shuffled) {
            Coord tail = field.normalize(center.move(tailDir));

            if (!occupiedByFood.contains(center) && !occupiedByFood.contains(tail)) {
                Snake snake = Snake.createNew(player.getId(), center, tailDir);

                state.addSnake(snake);
                state.addPlayer(player);

                return Optional.of(snake);
            }
        }

        return Optional.empty();
    }


    public Optional<Snake> spawnSnakeForPlayer(int playerId) {
        Set<Coord> occupiedBySnakes = collisionDetector.getOccupiedBySnakes(state.getSnakes());
        Set<Coord> occupiedByFood = new HashSet<>(state.getFoods());

        Optional<Coord> centerOpt = field.findFreeSquare(
                Config.NEW_SNAKE_SQUARE_SIZE, occupiedBySnakes, occupiedByFood);

        if (centerOpt.isEmpty()) {
            return Optional.empty();
        }

        Coord center = centerOpt.get();

        Direction[] directions = Direction.values();
        List<Direction> shuffled = new ArrayList<>(Arrays.asList(directions));
        Collections.shuffle(shuffled);

        for (Direction tailDir : shuffled) {
            Coord tail = field.normalize(center.move(tailDir));

            if (!occupiedByFood.contains(center) && !occupiedByFood.contains(tail)) {
                Snake snake = Snake.createNew(playerId, center, tailDir);
                state.addSnake(snake);
                return Optional.of(snake);
            }
        }

        return Optional.empty();
    }

    public void setPlayerRole(int playerId, NodeRole role) {
        state.getPlayerById(playerId).ifPresent(player -> {
            player.setRole(role);
        });
    }

    public boolean canJoin() {
        Set<Coord> occupiedBySnakes = collisionDetector.getOccupiedBySnakes(state.getSnakes());
        Set<Coord> occupiedByFood = new HashSet<>(state.getFoods());

        return field.findFreeSquare(Config.NEW_SNAKE_SQUARE_SIZE,
                occupiedBySnakes, occupiedByFood).isPresent();
    }

    public int getNextPlayerId() {
        return state.getNextPlayerId();
    }
}