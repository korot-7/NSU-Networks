package lab4.snake.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import lab4.snake.model.*;
import lab4.snake.network.controller.NetworkManager;
import lab4.snake.view.GameFieldView;
import lab4.snake.view.ScoreboardView;

public class GameController {
    @FXML private Pane canvasContainer;
    @FXML private Canvas gameCanvas;
    @FXML private VBox scoreboardContainer;
    @FXML private Label gameNameLabel;
    @FXML private Label roleLabel;
    @FXML private Label stateLabel;

    private AppController app;
    private NetworkManager networkManager;
    private GameFieldView fieldView;
    private ScoreboardView scoreboardView;

    public void setApp(AppController app) {
        this.app = app;
    }

    public void setNetworkManager(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @FXML
    public void initialize() {
        fieldView = new GameFieldView(gameCanvas);
        scoreboardView = new ScoreboardView(scoreboardContainer);

        gameCanvas.widthProperty().bind(canvasContainer.widthProperty());
        gameCanvas.heightProperty().bind(canvasContainer.heightProperty());

        gameCanvas.widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        gameCanvas.heightProperty().addListener((obs, oldVal, newVal) -> redraw());

        gameCanvas.setFocusTraversable(true);
        gameCanvas.setOnKeyPressed(this::handleKeyPress);
    }

    public void initNetwork() {
        if (networkManager == null) {
            System.err.println("NetworkManager is null!");
            return;
        }

        networkManager.setOnStateUpdate(this::onStateUpdate);
        networkManager.setOnError(this::onError);
        networkManager.setOnRoleChange(this::onRoleChange);
        networkManager.setOnGameOver(this::onGameOver);

        updateGameNameLabel();
        updateRoleLabel();

        if (networkManager.getCurrentState() != null) {
            onStateUpdate(networkManager.getCurrentState());
        }
    }

    public void requestFocus() {
        Platform.runLater(() -> gameCanvas.requestFocus());
    }

    private void handleKeyPress(KeyEvent event) {
        Direction direction = null;

        switch (event.getCode()) {
            case W, UP -> direction = Direction.UP;
            case S, DOWN -> direction = Direction.DOWN;
            case A, LEFT -> direction = Direction.LEFT;
            case D, RIGHT -> direction = Direction.RIGHT;
            case ESCAPE -> {
                if (networkManager != null && networkManager.getCurrentState() != null) {
                    onExit();
                }
            }
        }

        if (direction != null && networkManager != null) {
            networkManager.steer(direction);
        }

        event.consume();
    }

    private void onStateUpdate(GameState state) {
        Platform.runLater(() -> {
            if (state == null) return;
            fieldView.render(state, networkManager.getMyPlayerId());
            scoreboardView.update(state.getPlayers(), networkManager.getMyPlayerId());
            stateLabel.setText("Ход: " + state.getStateOrder());
        });
    }

    private void onRoleChange(NodeRole newRole) {
        Platform.runLater(this::updateRoleLabel);
    }

    private void onError(String message) {
        Platform.runLater(() -> {
            if (app != null) {
                app.showError(message);
            }
        });
    }

    private void onGameOver() {
        Platform.runLater(() -> {
            if (app != null) {
                app.showInfo("Игра завершена");
                app.showMainMenu();
            }
        });
    }

    private void updateGameNameLabel() {
        String name = networkManager != null ? networkManager.getGameName() : null;
        gameNameLabel.setText("Игра: " + (name != null ? name : "..."));
    }

    private void updateRoleLabel() {
        if (networkManager == null) {
            roleLabel.setText("Роль: ...");
            return;
        }

        NodeRole role = networkManager.getMyRole();
        boolean isHost = networkManager.isHost();

        String roleText = role != null ? switch (role) {
            case MASTER -> "MASTER (хост)";
            case DEPUTY -> "DEPUTY (заместитель)";
            case NORMAL -> "Игрок";
            case VIEWER -> "Наблюдатель";
        } : "Подключение...";

        roleLabel.setText("Роль: " + roleText);
    }

    private void redraw() {
        if (networkManager == null) return;
        GameState state = networkManager.getCurrentState();
        if (state != null) {
            fieldView.render(state, networkManager.getMyPlayerId());
        }
    }

    @FXML
    private void onExit() {
        if (networkManager != null) {
            networkManager.leaveGame();
        }
        if (app != null) {
            app.showMainMenu();
        }
    }
}