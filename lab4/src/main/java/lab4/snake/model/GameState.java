package lab4.snake.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GameState {
    private int stateOrder;
    private final List<Snake> snakes;
    private final List<Coord> foods;
    private final List<GamePlayer> players;
    private final GameConfig config;

    public GameState(int stateOrder, List<Snake> snakes, List<Coord> foods,
                     List<GamePlayer> players, GameConfig config) {
        this.stateOrder = stateOrder;
        this.snakes = new ArrayList<>(snakes);
        this.foods = new ArrayList<>(foods);
        this.players = new ArrayList<>(players);
        this.config = config;
    }

    public static GameState initial(GameConfig config) {
        return new GameState(0, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), config);
    }

    public int getStateOrder() {
        return stateOrder;
    }

    public void incrementStateOrder() {
        this.stateOrder++;
    }

    public List<Snake> getSnakes() {
        return snakes;
    }

    public List<Coord> getFoods() {
        return foods;
    }

    public List<GamePlayer> getPlayers() {
        return players;
    }

    public GameConfig getConfig() {
        return config;
    }

    public Optional<Snake> getSnakeByPlayerId(int playerId) {
        Optional<Snake> alive = snakes.stream()
                .filter(s -> s.getPlayerId() == playerId && s.getState() == SnakeState.ALIVE)
                .findFirst();

        if (alive.isPresent()) {
            return alive;
        }

        return snakes.stream()
                .filter(s -> s.getPlayerId() == playerId)
                .findFirst();
    }

    public Optional<GamePlayer> getPlayerById(int playerId) {
        return players.stream()
                .filter(p -> p.getId() == playerId)
                .findFirst();
    }

    public void addSnake(Snake snake) {
        snakes.add(snake);
    }

    public void removeSnake(int playerId) {
        snakes.removeIf(s -> s.getPlayerId() == playerId);
    }

    public void addPlayer(GamePlayer player) {
        players.add(player);
    }

    public void removePlayer(int playerId) {
        players.removeIf(p -> p.getId() == playerId);
    }

    public void addFood(Coord food) {
        foods.add(food);
    }


    public void addFoods(List<Coord> newFoods) {
        foods.addAll(newFoods);
    }


    public void removeFood(Coord food) {
        foods.remove(food);
    }

    public Optional<GamePlayer> getMaster() {
        return players.stream()
                .filter(p -> p.getRole() == NodeRole.MASTER)
                .findFirst();
    }

    public Optional<GamePlayer> getDeputy() {
        return players.stream()
                .filter(p -> p.getRole() == NodeRole.DEPUTY)
                .findFirst();
    }

    public int getNextPlayerId() {
        return players.stream()
                .mapToInt(GamePlayer::getId)
                .max()
                .orElse(0) + 1;
    }


    public GameState copy() {
        List<Snake> snakesCopy = snakes.stream()
                .map(Snake::copy)
                .collect(Collectors.toList());

        List<Coord> foodsCopy = new ArrayList<>(foods);

        List<GamePlayer> playersCopy = players.stream()
                .map(p -> new GamePlayer(p.getId(), p.getName(), p.getAddress(),
                        p.getRole(), p.getType(), p.getScore()))
                .collect(Collectors.toList());

        return new GameState(stateOrder, snakesCopy, foodsCopy, playersCopy, config);
    }
}